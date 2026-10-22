package com.noor.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.noor.tv.ui.theme.NoorColors

/** Focusable card: D-pad friendly, scales up and shows a gold border when focused. */
@Composable
fun TvCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    color: Color = NoorColors.Card,
    focusedColor: Color = NoorColors.CardFocused,
    accent: Color = NoorColors.Gold,
    corner: Dp = 14.dp,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .graphicsLayer {
                val s = if (focused) 1.06f else 1f
                scaleX = s; scaleY = s
            }
            .clip(shape)
            .background(if (focused) focusedColor else color)
            .border(if (focused) 3.dp else 1.dp, if (focused) accent else Color(0x22FFFFFF), shape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) { content(focused) }
}

@Composable
fun PillButton(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    TvCard(
        modifier = modifier,
        onClick = onClick,
        color = if (selected) NoorColors.Green else NoorColors.Card,
        focusedColor = if (selected) NoorColors.GreenLight else NoorColors.CardFocused,
        corner = 24.dp,
    ) {
        Text(
            label,
            color = if (selected) NoorColors.Gold else NoorColors.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = NoorColors.Gold,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(bottom = 12.dp),
    )
}

@Composable
fun ImageTile(
    title: String,
    subtitle: String?,
    image: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    accent: Color = NoorColors.Gold,
    imageHeight: Dp = 110.dp,
) {
    TvCard(modifier = modifier, onClick = onClick, accent = accent) { focused ->
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .background(NoorColors.Green),
                contentAlignment = Alignment.Center,
            ) {
                Text(placeholder, fontSize = 42.sp, color = accent)
                if (!image.isNullOrBlank()) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (favorite) {
                    Text("★", color = NoorColors.Gold, fontSize = 22.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    title,
                    color = if (focused) NoorColors.Gold else NoorColors.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = NoorColors.TextDim, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(NoorColors.Card)
            .border(if (focused) 3.dp else 1.dp, if (focused) NoorColors.Gold else Color(0x33FFFFFF),
                RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) Text("🔍  $hint", color = NoorColors.TextDim, fontSize = 18.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = NoorColors.Text, fontSize = 18.sp),
            cursorBrush = SolidColor(NoorColors.Gold),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
    }
}

@Composable
fun CenterMessage(text: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, color = NoorColors.TextDim, fontSize = 20.sp, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            PillButton("إعادة المحاولة", onClick = onRetry)
        }
    }
}

@Composable
fun Loading() = CenterMessage("جارٍ التحميل…")

@Composable
fun RowGap(w: Dp = 12.dp) = Spacer(Modifier.width(w))

@Composable
fun IconButtonTv(symbol: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    TvCard(
        modifier = modifier.size(52.dp),
        onClick = onClick,
        color = if (active) NoorColors.Green else NoorColors.Card,
        corner = 26.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(symbol, fontSize = 22.sp, color = if (active) NoorColors.Gold else NoorColors.Text)
        }
    }
}

@Composable
fun KeyValueRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = NoorColors.Text, fontSize = 20.sp, modifier = Modifier.width(260.dp))
        content()
    }
}
