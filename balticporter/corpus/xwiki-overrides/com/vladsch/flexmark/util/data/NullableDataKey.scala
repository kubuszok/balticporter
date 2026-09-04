/*
 * HANDWRITTEN OVERRIDE — Baltic Porter PLAN §7 whole-file override.
 *
 * Ported from: flexmark-java/flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/NullableDataKey.java
 * Original license: BSD-2-Clause (see flexmark-java upstream)
 * upstream-commit: cold-port
 */
package com.vladsch.flexmark.util.data

/**
 * Creates a DataKey with nullable data value and factory with non-nullable dataHolder
 * <p>
 * Use this constructor to ensure that factory is never called with null data holder value */
class NullableDataKey[T](name: String, defaultValue: T, factory: DataValueFactory[T]) extends DataKeyBase[T](name, defaultValue, factory) {
  /**
   * Create a DataKey with null default value and factory producing null values
   *
   * @param name key name
   */
  def this(name: String) =
    this(name, null.asInstanceOf[T], ((options: DataHolder) => null.asInstanceOf[T]))

  /**
   * Creates a NullableDataKey with a dynamic default value taken from a value of another key
   * <p>
   * does not cache the returned default value but will always delegate to another key until this key
   * gets its own value set. */
  def this(name: String, defaultKey: DataKeyBase[T]) =
    this(name, defaultKey.getDefaultValue(), defaultKey.get)

  /**
   * Creates a DataKey with a computed default value dynamically. */
  def this(name: String, factory: DataValueNullableFactory[T]) =
    this(name, factory.apply(null), factory)

  def this(name: String, defaultValue: T) =
    this(name, defaultValue, ((options: DataHolder) => defaultValue))

  /**
   * Creates a DataKey with nullable data value and factory not dependent on data holder
   * <p>
   * Use this constructor to ensure that factory is never called with null data holder value */
  def this(name: String, supplier: java.util.function.Supplier[T]) =
    this(name, supplier.get(), ((holder: DataHolder) => supplier.get()))

  def getDefaultValue(): T = {
    super.getDefaultValue()
  }

  def getDefaultValue(holder: DataHolder): T = {
    super.getDefaultValue(holder)
  }

  def get(holder: DataHolder): T = {
    super.get(holder)
  }

  override def set(dataHolder: MutableDataHolder, value: T): MutableDataHolder = {
    dataHolder.set(this, value)
  }

  override def toString(): String = {
    // factory applied to null in constructor, no sense doing it again here
    val defaultValue: T = getDefaultValue()
    if ((defaultValue != null)) {
      return ((("DataKey<" + defaultValue.getClass().getSimpleName()) + "> ") + getName())
    } else {
      return ("DataKey<null> " + getName())
    }
  }

}
