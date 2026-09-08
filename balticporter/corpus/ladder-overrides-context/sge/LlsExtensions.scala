package sge

extension [A](da: lowlevel.util.DynamicArray[A]) {
  def length: Int = da.size
}
// OrderedSet.head is now provided by the lls port (Array#first renamed to head)
