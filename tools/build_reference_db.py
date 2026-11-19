#!/usr/bin/env python3
"""
Справочная база приложения (app/src/main/assets/reference.db), только для чтения:
  - российские товары Open Food Facts с полным КБЖУ на 100 г (ODbL);
  - справочник USDA SR Legacy для продуктов без этикетки (CC0), список — tools/usda_ref.json.

Запуск:
  pip install duckdb
  python3 tools/build_reference_db.py                      # читает выгрузку OFF с Hugging Face по частям
  python3 tools/build_reference_db.py --off ru_raw.parquet # уже извлечённые российские товары (кэш)

Выгрузка OFF: https://huggingface.co/datasets/openfoodfacts/product-database (food.parquet, обновляется ежедневно).
Пустые числа в OFF не досчитываются: товар без любого из четырёх чисел в базу не попадает.
Единственный пересчёт — ккал из кДж (÷ 4,184), если ккал в карточке нет; такие строки помечены kcal_from_kj = 1.
"""
import argparse
import json
import os
import sqlite3
import sys
import time

OFF_URL = "https://huggingface.co/datasets/openfoodfacts/product-database/resolve/main/food.parquet"
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "reference.db")


def extract_off(source: str, cache: str | None):
    import duckdb

    con = duckdb.connect()
    if source.startswith("http"):
        con.execute("INSTALL httpfs; LOAD httpfs; SET http_timeout=600000;")
    else:
        cols = [r[0] for r in con.execute(f"DESCRIBE SELECT * FROM read_parquet('{source}')").fetchall()]
        if "nut" in cols:  # уже извлечённые российские товары (кэш прошлого запуска)
            return con.execute(f"SELECT code, lang, brands, last_modified_t, names, nut FROM read_parquet('{source}')").fetchall()
    query = f"""
        SELECT code, lang, brands, last_modified_t, product_name AS names,
          list_filter(nutriments, lambda x: x.name IN ('energy-kcal','energy-kj','energy','proteins','fat','carbohydrates','fiber')) AS nut
        FROM read_parquet('{source}')
        WHERE list_contains(countries_tags, 'en:russia')
    """
    if cache and source.startswith("http"):
        con.execute(f"COPY ({query}) TO '{cache}' (FORMAT parquet)")
        query = f"SELECT * FROM read_parquet('{cache}')"
    return con.execute(query).fetchall()


def norm_code(code: str | None) -> str | None:
    if not code:
        return None
    c = code.strip()
    if not c.isdigit():
        return None
    if len(c) == 14:
        c = c[1:] if c.startswith("0") else c
    return c if len(c) in (8, 12, 13) else None


def pick_name(names, lang):
    if not names:
        return None
    by = {}
    for n in names:
        lang_key, text = n["lang"], (n["text"] or "").strip()
        if text:
            by.setdefault(lang_key, text)
    for key in ("ru", "main", lang):
        if key in by:
            return by[key]
    return next(iter(by.values()), None)


def search_key(name: str, brand: str | None) -> str:
    return " ".join(x for x in (name, brand) if x).lower().replace("ё", "е").strip()


def off_rows(raw):
    best = {}
    for code, lang, brands, modified, names, nut in raw:
        c = norm_code(code)
        name = pick_name(names, lang)
        if not c or not name:
            continue
        v = {}
        for n in nut or []:
            if n["100g"] is not None and n["name"] not in v:
                v[n["name"]] = float(n["100g"])
        kcal = v.get("energy-kcal")
        kj = v.get("energy-kj", v.get("energy"))
        from_kj = kcal is None and kj is not None
        if from_kj:
            kcal = kj / 4.184
        p, f, carbs = v.get("proteins"), v.get("fat"), v.get("carbohydrates")
        if None in (kcal, p, f, carbs):
            continue
        if not (0 <= kcal <= 950 and 0 <= p <= 100 and 0 <= f <= 100 and 0 <= carbs <= 100):
            continue
        brand = (brands or "").split(",")[0].strip() or None
        fiber = v.get("fiber")
        # parquet хранит float32: 8.8 приходит как 8.800000190734863
        row = (c, name, brand, round(kcal, 1), round(p, 2), round(f, 2), round(carbs, 2),
               None if fiber is None else round(fiber, 2), 1 if from_kj else 0, modified or 0)
        if c not in best or row[-1] > best[c][-1]:
            best[c] = row
    return list(best.values())


def build(off, usda_items, out_path, off_note, dishes=()):
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    if os.path.exists(out_path):
        os.remove(out_path)
    db = sqlite3.connect(out_path)
    db.executescript(
        """
        CREATE TABLE ref_product (
          id INTEGER PRIMARY KEY,
          source TEXT NOT NULL,            -- off | usda
          source_id TEXT NOT NULL,         -- код OFF, fdcId USDA или chat-NN (мои блюда)
          barcode TEXT,
          name TEXT NOT NULL,
          brand TEXT,
          search_key TEXT NOT NULL,        -- нижний регистр, ё → е
          kcal REAL NOT NULL, protein REAL NOT NULL, fat REAL NOT NULL, carbs REAL NOT NULL, fiber REAL,
          kcal_from_kj INTEGER NOT NULL DEFAULT 0,
          swell_k REAL, swell_k_min REAL, swell_k_max REAL
        );
        CREATE INDEX idx_ref_barcode ON ref_product(barcode);
        CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
        """
    )
    for it in usda_items:
        db.execute(
            "INSERT INTO ref_product (source, source_id, barcode, name, brand, search_key, kcal, protein, fat, carbs, fiber, swell_k, swell_k_min, swell_k_max) "
            "VALUES ('usda', ?, NULL, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (str(it["fdc_id"]), it["name_ru"], search_key(it["name_ru"], None), it["kcal"], it["protein"], it["fat"], it["carbs"],
             it.get("fiber"), it.get("swell_k"), it.get("swell_k_min"), it.get("swell_k_max")),
        )
    # мои блюда из чата: КБЖУ на 100 г готового, берутся в свою базу без сверки
    for it in dishes:
        db.execute(
            "INSERT INTO ref_product (source, source_id, barcode, name, brand, search_key, kcal, protein, fat, carbs, fiber) "
            "VALUES ('chat', ?, NULL, ?, NULL, ?, ?, ?, ?, ?, ?)",
            (it["id"], it["name"], search_key(it["name"], None), it["kcal"], it["protein"], it["fat"], it["carbs"], it.get("fiber")),
        )
    for c, name, brand, kcal, p, f, carbs, fiber, from_kj, _ in off:
        db.execute(
            "INSERT INTO ref_product (source, source_id, barcode, name, brand, search_key, kcal, protein, fat, carbs, fiber, kcal_from_kj) "
            "VALUES ('off', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (c, c, name, brand, search_key(name, brand), kcal, p, f, carbs, fiber, from_kj),
        )
    meta = {
        "built_at": time.strftime("%Y-%m-%d"),
        "off_count": str(len(off)),
        "usda_count": str(len(usda_items)),
        "chat_count": str(len(dishes)),
        "off_source": off_note,
        "off_license": "Open Database License (ODbL) — Open Food Facts contributors",
        "usda_source": "USDA FoodData Central SR Legacy, CC0 1.0",
    }
    db.executemany("INSERT INTO meta VALUES (?, ?)", meta.items())
    db.commit()
    db.execute("VACUUM")
    db.close()
    return meta


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--off", default=OFF_URL, help="food.parquet OFF или уже извлечённые российские товары")
    ap.add_argument("--cache", default=None, help="куда сохранить извлечённые российские товары (parquet)")
    ap.add_argument("--usda", default=os.path.join(HERE, "usda_ref.json"))
    ap.add_argument("--dishes", default=os.path.join(HERE, "my_dishes.json"))
    ap.add_argument("--out", default=OUT)
    a = ap.parse_args()
    t = time.time()
    raw = extract_off(a.off, a.cache)
    off = off_rows(raw)
    usda = json.load(open(a.usda, encoding="utf-8"))["items"]
    dishes = json.load(open(a.dishes, encoding="utf-8"))["items"] if os.path.exists(a.dishes) else []
    meta = build(off, usda, a.out, "Open Food Facts food.parquet (Hugging Face), " + time.strftime("%Y-%m-%d"), dishes)
    size = os.path.getsize(a.out)
    print(f"Российских товаров в выгрузке: {len(raw)}, с полным КБЖУ: {len(off)}; USDA: {len(usda)}; мои блюда: {len(dishes)}")
    print(f"{a.out}: {size / 1024 / 1024:.1f} МБ, {time.time() - t:.0f} с; {meta['built_at']}")


if __name__ == "__main__":
    sys.exit(main())
