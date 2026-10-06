package com.nitomash.bpmplayer.data.playlist.xspf

import android.util.Xml
import java.io.StringReader

class AndroidXspfTagSource(xml: String) : XspfTagSource {

    private val state: Any = Xml.newPullParser().apply { setInput(StringReader(xml)) }

    override fun next(): XspfToken {
        val parser = state as org.xmlpull.v1.XmlPullParser
        var event = parser.next()
        while (event != org.xmlpull.v1.XmlPullParser.START_TAG &&
            event != org.xmlpull.v1.XmlPullParser.END_TAG &&
            event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT
        ) {
            event = parser.next()
        }
        val name = parser.name.orEmpty().substringAfterLast(':')
        return when (event) {
            org.xmlpull.v1.XmlPullParser.START_TAG -> XspfToken.Start(name)
            org.xmlpull.v1.XmlPullParser.END_TAG -> XspfToken.End(name)
            else -> XspfToken.DocumentEnd
        }
    }

    override fun readText(): String {
        val parser = state as org.xmlpull.v1.XmlPullParser
        val depth = parser.depth
        val builder = StringBuilder()
        var event = parser.next()
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (event == org.xmlpull.v1.XmlPullParser.END_TAG && parser.depth == depth) break
            if (event == org.xmlpull.v1.XmlPullParser.TEXT) builder.append(parser.text)
            event = parser.next()
        }
        return builder.toString()
    }
}