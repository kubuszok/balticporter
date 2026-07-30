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
 * Use this constructor to ensure that factory is never called with null data holder value
 *
 * @param name         See {@link #getName()}.
 * @param defaultValue default to use when data holder is null
 * @param factory      data value factory for creating a new default value for the key for a non-null data holder
 */
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
   * gets its own value set.
   *
   * @param name       See {@link #getName()}.
   * @param defaultKey The NullableDataKey to take the default value from at time of construction.
   */
  def this(name: String, defaultKey: DataKeyBase[T]) =
    this(name, defaultKey.getDefaultValue(), defaultKey.get)

  /**
   * Creates a DataKey with a computed default value dynamically.
   * <p>
   * On construction will invoke factory with null data holder to get the default value
   *
   * @param name    See {@link #getName()}.
   * @param factory data value factory for creating a new default value for the key
   */
  def this(name: String, factory: DataValueNullableFactory[T]) =
    this(name, factory.apply(null), factory)

  def this(name: String, defaultValue: T) =
    this(name, defaultValue, ((options: DataHolder) => defaultValue))

  /**
   * Creates a DataKey with nullable data value and factory not dependent on data holder
   * <p>
   * Use this constructor to ensure that factory is never called with null data holder value
   *
   * @param name     See {@link #getName()}.
   * @param supplier data value factory for creating a new default value for the key not dependent on dataHolder
   */
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
