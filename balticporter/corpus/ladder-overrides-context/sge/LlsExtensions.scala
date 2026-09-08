package sge

extension [A](da: lowlevel.util.DynamicArray[A]) {
  def length: Int = da.size
}
extension [A <: AnyRef](os: lowlevel.util.OrderedSet[A]) {
  def head: A = os.first
}
