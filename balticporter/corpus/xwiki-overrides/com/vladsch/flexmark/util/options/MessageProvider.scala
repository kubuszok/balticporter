/*
 * HANDWRITTEN OVERRIDE — Baltic Porter PLAN §7 whole-file override.
 *
 * Ported from: flexmark-java/flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/MessageProvider.java
 * Original license: BSD-2-Clause (see flexmark-java upstream)
 *
 * Why hand-ported: the SAM method `message` takes varargs (`Object...` -> `Any*`),
 * but the DEFAULT lambda's resolved param type is the array form `Array[AnyRef]`, so
 * the function literal does not SAM-convert to MessageProvider. Implemented as an
 * explicit anonymous class with the varargs signature.
 */
package com.vladsch.flexmark.util.options

trait MessageProvider {
  def message(key: String, defaultText: String, params: Any*): String

}

object MessageProvider {
  val DEFAULT: MessageProvider = new MessageProvider {
    override def message(key: String, defaultText: String, params: Any*): String = {
      if (((params.length > 0) && (defaultText.indexOf('{') >= 0))) {
        java.text.MessageFormat.format(defaultText, params.map(_.asInstanceOf[AnyRef])*)
      }
      else defaultText
    }
  }

}
