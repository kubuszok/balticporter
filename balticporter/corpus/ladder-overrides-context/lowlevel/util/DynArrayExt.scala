package lowlevel.util

extension [A](da: DynamicArray[A]) {
  def length: Int = da.size
}
extension [A <: AnyRef](os: OrderedSet[A]) {
  def head: A = os.first
}
