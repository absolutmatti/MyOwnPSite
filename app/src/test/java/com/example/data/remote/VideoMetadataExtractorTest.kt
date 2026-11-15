package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VideoMetadataExtractorTest {

    @Test
    fun extractEpornerVideoId_parsesStandardAndHdUrls() {
        assertEquals(
            "ndVyU3DlOF0",
            VideoMetadataExtractor.extractEpornerVideoId(
                "https://www.eporner.com/video-ndVyU3DlOF0/monica-minx-and-rocket-powers-get-slippery-and-steamy-in-an-oiled-adventure/"
            )
        )
        assertEquals(
            "AbCd1234",
            VideoMetadataExtractor.extractEpornerVideoId(
                "https://eporner.com/hd-porn/AbCd1234/sample-video/"
            )
        )
        assertNull(
            VideoMetadataExtractor.extractEpornerVideoId(
                "https://www.porn00.com/video/mandy-rhea-beautiful-stepmom/"
            )
        )
    }

    @Test
    fun deriveReadableTitleFromUrl_formatsEpornerAndPorn00Slugs() {
        assertEquals(
            "Mandy Rhea Beautiful Stepmom",
            VideoMetadataExtractor.deriveReadableTitleFromUrl(
                "https://www.porn00.com/video/mandy-rhea-beautiful-stepmom/"
            )
        )
        assertEquals(
            "Monica Minx And Rocket Powers Get Slippery And Steamy In An Oiled Adventure",
            VideoMetadataExtractor.deriveReadableTitleFromUrl(
                "https://www.eporner.com/video-ndVyU3DlOF0/monica-minx-and-rocket-powers-get-slippery-and-steamy-in-an-oiled-adventure/"
            )
        )
    }

    @Test
    fun parseEpornerApiJson_handlesDirectObjectAndVideosArray() {
        val arrayJson = """
            {
              "videos": [
                {
                  "id": "ndVyU3DlOF0",
                  "title": "Monica Minx &amp; Rocket Powers",
                  "default_thumb": {
                    "src": "https://static.eporner.com/thumbs/static4/1/2/3/thumb.jpg"
                  }
                }
              ]
            }
        """.trimIndent()

        val parsedArray = VideoMetadataExtractor.parseEpornerApiJson(arrayJson)
        assertEquals("Monica Minx & Rocket Powers", parsedArray?.title)
        assertEquals(
            "https://static.eporner.com/thumbs/static4/1/2/3/thumb.jpg",
            parsedArray?.thumbnailUrl
        )

        val directJson = """
            {
              "id": "ndVyU3DlOF0",
              "title": "Direct Video Title",
              "thumbs": [
                { "src": "//static.eporner.com/thumbs/big.jpg" }
              ]
            }
        """.trimIndent()

        val parsedDirect = VideoMetadataExtractor.parseEpornerApiJson(directJson)
        assertEquals("Direct Video Title", parsedDirect?.title)
        assertEquals("https://static.eporner.com/thumbs/big.jpg", parsedDirect?.thumbnailUrl)
    }

    @Test
    fun parseHtmlMetadata_extractsOpenGraphJsonLdAndVideoPoster() {
        val html = """
            <!doctype html>
            <html>
            <head>
              <title>Mandy Rhea - Beautiful Stepmom - Porn00</title>
              <meta content="Mandy Rhea &#039;Beautiful Stepmom&#039; - Porn00" property="og:title" />
              <meta property="og:image" content="https://cdn.porn00.com/thumbs/mandy-123.jpg" />
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val parsed = VideoMetadataExtractor.parseHtmlMetadata(
            html = html,
            baseUrl = "https://www.porn00.com/video/mandy-rhea-beautiful-stepmom/",
        )
        assertEquals("Mandy Rhea 'Beautiful Stepmom'", parsed.title)
        assertEquals("https://cdn.porn00.com/thumbs/mandy-123.jpg", parsed.thumbnailUrl)

        val jsonLdHtml = """
            <html><head>
            <script type="application/ld+json">
            {
              "@type": "VideoObject",
              "name": "JsonLd Clip Title",
              "thumbnailUrl": ["https://cdn.example.com/poster-99.jpg"]
            }
            </script>
            </head><body></body></html>
        """.trimIndent()
        val parsedJsonLd = VideoMetadataExtractor.parseHtmlMetadata(jsonLdHtml, "https://www.porn00.com/video/x/")
        assertEquals("JsonLd Clip Title", parsedJsonLd.title)
        assertEquals("https://cdn.example.com/poster-99.jpg", parsedJsonLd.thumbnailUrl)
    }
}
