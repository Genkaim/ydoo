package com.genkaim.zeppglass.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.BuildConfig
import com.genkaim.zeppglass.R
import com.genkaim.zeppglass.ui.components.FlatPanel
import com.genkaim.zeppglass.ui.components.PinnedPageHeader
import com.genkaim.zeppglass.ui.components.SectionTitle

private const val AUTHOR = "Genkaim"
private const val AUTHOR_URL = "https://github.com/Genkaim"
private const val PROJECT_URL = "https://github.com/Genkaim/ydoo"

private data class Reference(
    val name: String,
    val description: String,
    val licence: String,
    val url: String? = null
)

/**
 * The open-source work this app stands on. ZeppBridge (MIT) is the external
 * project whose Zepp endpoints, login flow and parsing conventions this app
 * follows; the per-second workout encoding was checked against it. MIT allows
 * reuse as long as the copyright and licence notice are kept, so the card links
 * to the project and names the licence.
 */
private val references = listOf(
    Reference(
        name = "ZeppBridge",
        description = "外部开源项目：Zepp 接口、登录流程与数据口径的参考实现",
        licence = "MIT License",
        url = "https://github.com/lingcang728/ZeppBridge"
    )
)

/**
 * About page, laid out like the About screen of the 掌上教务 app: logo and name
 * at the top, then a 项目 group (author, project address) and an 开源引用 group
 * with one card per project.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
            )
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(headerHeight + 4.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0xFFF2F2F2)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.brand_mark),
                        contentDescription = "ydoo",
                        modifier = Modifier.size(54.dp)
                    )
                }
                Text(
                    "ydoo",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "ydoo · ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AboutSection("项目") {
                AboutLinkCard(
                    title = "作者",
                    description = AUTHOR,
                    onClick = { openUrl(AUTHOR_URL) },
                    leading = {
                        Image(
                            painter = painterResource(R.drawable.author_avatar),
                            contentDescription = "作者头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                    }
                )
                Spacer(Modifier.height(4.dp))
                AboutLinkCard(
                    title = "项目地址",
                    description = "github.com/Genkaim/ydoo",
                    onClick = { openUrl(PROJECT_URL) },
                    leading = {
                        Icon(
                            Icons.Rounded.Code,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
            }

            AboutSection("开源引用") {
                references.forEachIndexed { index, reference ->
                    if (index > 0) Spacer(Modifier.height(4.dp))
                    ReferenceCard(reference) {
                        reference.url?.let(openUrl)
                    }
                }
            }
        }

        PinnedPageHeader(
            modifier = Modifier.align(Alignment.TopCenter),
            onHeight = { headerHeight = it }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                }
                Text("关于", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

@Composable
private fun AboutLinkCard(
    title: String,
    description: String,
    onClick: (() -> Unit)?,
    leading: @Composable () -> Unit
) {
    FlatPanel(
        Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        onClick = onClick
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onClick != null) {
                Icon(
                    Icons.Rounded.OpenInNew,
                    contentDescription = "打开链接",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun ReferenceCard(reference: Reference, onClick: () -> Unit) {
    FlatPanel(
        Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        onClick = reference.url?.let { { onClick() } }
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(reference.name, fontWeight = FontWeight.SemiBold)
                Text(
                    reference.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    reference.licence,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (reference.url != null) {
                Spacer(Modifier.size(12.dp))
                Icon(
                    Icons.Rounded.OpenInNew,
                    contentDescription = "查看开源项目",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}



