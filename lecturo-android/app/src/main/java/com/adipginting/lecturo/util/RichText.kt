package com.adipginting.lecturo.util

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A message: the cheap Markdown renderer while there is no formula in it, and
 * the maths view only when there is. A WebView per message is too much to pay
 * for prose.
 */
@Composable
fun RichText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val hasMath = remember(markdown) { splitMath(markdown).any { it is MathPart.Math } }
    if (MathSetting.enabled && hasMath) {
        MathView(markdown = markdown, modifier = modifier, color = style.color, fontSize = style.fontSize.value)
    } else {
        MarkdownText(markdown = markdown, modifier = modifier, style = style, maxLines = maxLines, overflow = overflow)
    }
}

/** Renders one message's prose and formulas with the bundled KaTeX. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MathView(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color,
    fontSize: Float,
) {
    var heightPx by remember { mutableIntStateOf(0) }
    val html = remember(markdown, color, fontSize) {
        mathHtml(markdown, "#%06X".format(0xFFFFFF and color.toArgb()), fontSize)
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .then(
                // The web view reports CSS pixels, which are device-independent —
                // the same unit `dp` is.
                if (heightPx > 0) {
                    Modifier.height(heightPx.dp)
                } else {
                    Modifier.heightIn(min = 24.dp)
                },
            ),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onHeight(px: Int) {
                            Handler(Looper.getMainLooper()).post { heightPx = px }
                        }
                    },
                    "Android",
                )
            }
        },
        update = { web ->
            if (web.tag != html) {
                web.tag = html
                web.loadDataWithBaseURL("file:///android_asset/katex/", html, "text/html", "utf-8", null)
            }
        },
    )
}

private fun mathHtml(markdown: String, color: String, fontSize: Float): String {
    val body = markdownToHtml(markdown)
    return """
        <!doctype html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href="katex.min.css">
        <style>
          body { margin:0; padding:0; color:$color; font-size:${fontSize}px;
                 font-family:sans-serif; line-height:1.35; word-wrap:break-word; }
          p { margin:0 0 0.5em 0; }
          h1,h2,h3,h4,h5,h6 { margin:0.4em 0 0.2em 0; }
          blockquote { margin:0.3em 0; padding-left:0.6em; border-left:2px solid $color;
                       opacity:0.8; }
          code { font-family:monospace; }
          .math[data-display="true"] { margin:0.4em 0; }
        </style></head><body>$body</body>
        <script src="katex.min.js"></script>
        <script>
          function report() {
            Android.onHeight(Math.ceil(document.body.getBoundingClientRect().height) + 1);
          }
          document.querySelectorAll('.math').forEach(function (el) {
            try {
              katex.render(el.getAttribute('data-tex'), el, {
                displayMode: el.getAttribute('data-display') === 'true',
                throwOnError: false
              });
            } catch (e) {
              el.textContent = el.getAttribute('data-tex');
            }
          });
          // Display maths grows once KaTeX's fonts land, so measure again then —
          // and on every later reflow.
          if (window.ResizeObserver) new ResizeObserver(report).observe(document.body);
          if (document.fonts && document.fonts.ready) document.fonts.ready.then(report);
          window.addEventListener('load', report);
          requestAnimationFrame(report);
          report();
        </script></html>
    """.trimIndent()
}
