package com.badlogic.gdx

trait Preferences {
  def putBoolean(key: java.lang.String, `val`: scala.Boolean): Preferences
  def putInteger(key: java.lang.String, `val`: scala.Int): Preferences
  def putLong(key: java.lang.String, `val`: scala.Long): Preferences
  def putFloat(key: java.lang.String, `val`: scala.Float): Preferences
  def putString(key: java.lang.String, `val`: java.lang.String): Preferences
  def put(vals: scala.collection.mutable.Map[java.lang.String, ?]): Preferences
  def getBoolean(key: java.lang.String): scala.Boolean
  def getInteger(key: java.lang.String): scala.Int
  def getLong(key: java.lang.String): scala.Long
  def getFloat(key: java.lang.String): scala.Float
  def getString(key: java.lang.String): java.lang.String
  def getBoolean(key: java.lang.String, defValue: scala.Boolean): scala.Boolean
  def getInteger(key: java.lang.String, defValue: scala.Int): scala.Int
  def getLong(key: java.lang.String, defValue: scala.Long): scala.Long
  def getFloat(key: java.lang.String, defValue: scala.Float): scala.Float
  def getString(key: java.lang.String, defValue: java.lang.String): java.lang.String
  def get(): scala.collection.mutable.Map[java.lang.String, ?]
  def contains(key: java.lang.String): scala.Boolean
  def clear(): scala.Unit
  def remove(key: java.lang.String): scala.Unit
  def flush(): scala.Unit
}