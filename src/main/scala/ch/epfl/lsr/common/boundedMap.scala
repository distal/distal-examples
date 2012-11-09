package ch.epfl.lsr.common

import scala.collection.immutable.TreeMap

object BoundedMap { 
  def empty[Key,Value](size :Int = 1000) (implicit ordering: Ordering[Key]) =
    new BoundedMap[Key,Value](size, new TreeMap[Key,Value]()(ordering))
}


class BoundedMap[A,B] private (maxSize :Int, tree : TreeMap[A,B]) { 
  val m = { if(tree.size < maxSize) tree else tree.drop(maxSize / 2) }

  def updated [B1 >: B](key :A, value :B1): BoundedMap[A, B1] = { 
    // assert(ordering.lte(m.firstKey, key)) 

    new BoundedMap[A, B1](maxSize, m.updated(key, value)) 
  }

  def get(key :A): Option[B] = m.get(key)
  def contains(key :A) :Boolean = m.contains(key)

  def filterKeys(p : A => Boolean) = m.filterKeys(p)
}
