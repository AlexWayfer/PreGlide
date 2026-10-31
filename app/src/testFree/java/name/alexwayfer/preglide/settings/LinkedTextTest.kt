package name.alexwayfer.preglide.settings

import androidx.compose.ui.text.LinkAnnotation
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkedTextTest {
    private val url = "https://example.test"

    @Test
    fun theNamedWordLeadsToTheUrl() {
        val text = "Sync with PreType too"

        val linked = linkedText(text, "PreType", url, styles = null)

        assertEquals(text, linked.text)
        val links = linked.getLinkAnnotations(0, text.length)
        assertEquals(1, links.size)
        assertEquals(text.indexOf("PreType"), links.single().start)
        assertEquals(text.indexOf("PreType") + "PreType".length, links.single().end)
        assertEquals(url, (links.single().item as LinkAnnotation.Url).url)
    }

    @Test
    fun aTextWithoutTheWordStaysPlain() {
        val linked = linkedText("Sync too", "PreType", url, styles = null)

        assertEquals("Sync too", linked.text)
        assertEquals(emptyList<Any>(), linked.getLinkAnnotations(0, linked.length))
    }
}
