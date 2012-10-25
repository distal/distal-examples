package ch.epfl.lsr.common


object Random extends scala.util.Random { 

  def nextElement[T](seq :IndexedSeq[T]) :T = seq(nextInt(seq.size))

}
