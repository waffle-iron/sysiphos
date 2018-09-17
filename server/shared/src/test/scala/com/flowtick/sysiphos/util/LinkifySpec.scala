package com.flowtick.sysiphos.util

import org.scalatest.{ FlatSpec, Matchers }

class LinkifySpec extends FlatSpec with Matchers {
  "Linkify" should "make link from urls" in {
    Linkify.linkify("http://example.org/foo/bar.html") should
      be(s"""<a target="_blank" href="http://example.org/foo/bar.html">http://example.org/foo/bar.html</a>""")
  }
}
