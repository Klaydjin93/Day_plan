package app.dayplan

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RemoteViews

/**
 * Robolectric не подключает RemoteCollectionItems к ListView (adapter = null),
 * поэтому для снимка элементы списка достаются из RemoteViews и раскладываются
 * вручную — так же, как их нарисовал бы лаунчер.
 */
object RemoteListRender {

    fun collectionItems(rv: RemoteViews): List<RemoteViews> {
        val out = mutableListOf<RemoteViews>()
        walk(rv, out, HashSet())
        return out
    }

    private fun walk(rv: RemoteViews, out: MutableList<RemoteViews>, seen: MutableSet<Int>) {
        if (!seen.add(System.identityHashCode(rv))) return
        val actions = fieldValue(rv, "mActions") as? List<*> ?: return
        for (action in actions.filterNotNull()) {
            var cls: Class<*>? = action.javaClass
            while (cls != null && cls != Any::class.java) {
                for (f in cls.declaredFields) {
                    f.isAccessible = true
                    when (val v = f.get(action)) {
                        is android.widget.RemoteViews.RemoteCollectionItems ->
                            for (i in 0 until v.itemCount) out += v.getItemView(i)
                        is RemoteViews -> walk(v, out, seen)
                    }
                }
                cls = cls.superclass
            }
        }
    }

    private fun fieldValue(obj: Any, name: String): Any? {
        var cls: Class<*>? = obj.javaClass
        while (cls != null) {
            runCatching { return cls!!.getDeclaredField(name).apply { isAccessible = true }.get(obj) }
            cls = cls.superclass
        }
        return null
    }

    /** Заменяет пустой ListView столбцом из отрисованных элементов. */
    fun fillLists(root: View, items: List<RemoteViews>, context: Context) {
        val list = findList(root) ?: return
        val parent = list.parent as ViewGroup
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        items.forEach { item ->
            column.addView(item.apply(context, column), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val index = parent.indexOfChild(list)
        val lp = list.layoutParams
        parent.removeView(list)
        parent.addView(column, index, lp)
    }

    private fun findList(v: View): ListView? = when (v) {
        is ListView -> v
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { findList(v.getChildAt(it)) }
        else -> null
    }
}
