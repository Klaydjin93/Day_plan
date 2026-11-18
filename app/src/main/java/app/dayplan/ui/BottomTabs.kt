package app.dayplan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tab(val title: String) { DAY("День"), FOOD("Питание"), WEIGHT("Вес") }

/** Нижние вкладки в стиле планов: моно-подпись, активная — с цветной чертой. */
@Composable
fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.Surface).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Palette.Border))
        Row(Modifier.fillMaxWidth()) {
            Tab.entries.forEach { t ->
                val active = t == selected
                Column(
                    Modifier.weight(1f).clickable { onSelect(t) }.padding(top = 10.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.width(28.dp).height(2.dp).background(if (active) Palette.Body else Palette.Surface))
                    Text(
                        t.title.uppercase(),
                        fontFamily = Fonts.Mono, fontSize = 11.sp, letterSpacing = 1.2.sp,
                        color = if (active) Palette.Text else Palette.Muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
