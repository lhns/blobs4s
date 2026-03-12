trait Blob {
  def size: Long

  def isEmpty: Boolean = size == 0

  def nonEmpty: Boolean = !isEmpty

  def get(index: Long): Byte


}
