package com.badlogic.gdx.net

object HttpParametersUtils {
  var defaultEncoding: java.lang.String = "UTF-8"
  var nameValueSeparator: java.lang.String = "="
  var parameterSeparator: java.lang.String = "&"
  def convertHttpParameters(parameters: scala.collection.mutable.Map[java.lang.String, java.lang.String]): java.lang.String = {
    val keySet: scala.collection.Set[java.lang.String] = parameters.keySet
    val convertedParameters: java.lang.StringBuilder = new java.lang.StringBuilder()
    for (name <- keySet) {
      convertedParameters.append(HttpParametersUtils.encode(name, HttpParametersUtils.defaultEncoding))
      convertedParameters.append(HttpParametersUtils.nameValueSeparator)
      convertedParameters.append(HttpParametersUtils.encode(parameters.getOrElse(name, null.asInstanceOf[java.lang.String]), HttpParametersUtils.defaultEncoding))
      convertedParameters.append(HttpParametersUtils.parameterSeparator)
    }
    if (convertedParameters.length() > 0) {
      convertedParameters.deleteCharAt(convertedParameters.length() - 1)
    } else ()
    return convertedParameters.toString()
  }
  private def encode(content: java.lang.String, encoding: java.lang.String): java.lang.String = {
    try {
      return java.net.URLEncoder.encode(content, encoding)
    } catch {
      case e: java.io.UnsupportedEncodingException => {
        throw new java.lang.IllegalArgumentException(e)
      }
    }
  }
}