/*
 * Copyright 2017 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.featran

import com.spotify.featran.transformers.Transformer

import scala.collection.immutable.SortedMap
import scala.collection.mutable

private object Crossings {
  type KEY = (String, String)
  type FN = (Double, Double) => Double
  type MAP = SortedMap[KEY, FN]
  def empty: Crossings = Crossings(SortedMap.empty, Set.empty)
  def name(n1: String, n2: String): String = "cross_%s_x_%s".format(n1, n2)
}

private case class Crossings(map: Crossings.MAP, keys: Set[String]) {

  def +(v: (Crossings.KEY, Crossings.FN)): Crossings = {
    val (k, f) = v
    require(!map.contains(k), s"Crossing $k already defined")
    this.copy(this.map + (k -> f), this.keys ++ Set(k._1, k._2))
  }

  def ++(that: Crossings): Crossings = {
    val k = this.map.keySet.intersect(that.map.keySet)
    require(k.isEmpty, s"Duplicate crossing ${k.mkString(", ")}")
    Crossings(this.map ++ that.map, this.keys ++ that.keys)
  }

  def filter[T](predicate: String => Boolean): Crossings = {
    val filteredKeys = keys.filter(predicate)
    val b = SortedMap.newBuilder[Crossings.KEY, Crossings.FN]
    b ++= map.filterKeys { case (k1, k2) =>
      filteredKeys.contains(k1) || filteredKeys.contains(k2)
    }
    val filteredMap = b.result()

    Crossings(filteredMap, filteredKeys)
  }

}

object CrossingFeatureBuilder {
  @inline def apply[F](fb: FeatureBuilder[F], crossings: Crossings): FeatureBuilder[F] =
    new CrossingFeatureBuilder[F](fb, crossings)
}

private class CrossingFeatureBuilder[F] private (
  private val fb: FeatureBuilder[F],
  private val crossings: Crossings
) extends FeatureBuilder[F] {
  private case class CrossValue(name: String, offset: Int, value: Double)
  private[this] var xEnabled = false // true if current transformer will be crossed
  // name, offset and values of the current transformer
  private[this] var xName: String = _
  private[this] var xOffset = 0
  private[this] var xQueue: mutable.Queue[CrossValue] = _
  // values and dimensions of transformers to be crossed
  private[this] val xValues = mutable.Map.empty[String, mutable.Queue[CrossValue]]
  private[this] val xDims = mutable.Map.empty[String, Int]

  override def prepare(transformer: Transformer[_, _, _]): Unit = {
    updateDim()
    val name = transformer.name
    if (crossings.keys.contains(name)) {
      xEnabled = true
      xName = name
      xOffset = 0
      xQueue = mutable.Queue.empty
      xValues(name) = xQueue
    } else {
      xEnabled = false
    }
  }

  // update dimension of the current transformer
  private def updateDim(): Unit =
    if (xEnabled) {
      xDims(xName) = xOffset
    }

  override def init(dimension: Int): Unit = fb.init(dimension)
  override def add(name: String, value: Double): Unit = {
    if (xEnabled) {
      xQueue.enqueue(CrossValue(name, xOffset, value))
      xOffset += 1
    }
    fb.add(name, value)
  }
  override def add[M[_]](names: Iterable[String], values: M[Double])(implicit
    ev: M[Double] => Seq[Double]
  ): Unit = {
    if (xEnabled) {
      val i = names.iterator
      val j = values.iterator
      while (i.hasNext && j.hasNext) {
        xQueue.enqueue(CrossValue(i.next(), xOffset, j.next()))
        xOffset += 1
      }
    }
    fb.add(names, values)
  }

  override def addInts[M[_]](names: Iterable[String], values: M[Int])(implicit
                                                                      ev: M[Int] => Seq[Int]): Unit = {
    if (xEnabled) {
      val i = names.iterator
      val j = values.iterator
      while (i.hasNext && j.hasNext) {
        xQueue.enqueue(CrossValue(i.next(), xOffset, j.next()))
        xOffset += 1
      }
    }
    fb.addInts(names, values)
  }

  override def skip(): Unit = {
    xOffset += 1
    fb.skip()
  }
  override def skip(n: Int): Unit = {
    xOffset += n
    fb.skip(n)
  }
  override def result: F = {
    updateDim()
    crossings.map.foreach { case ((t1, t2), f) =>
      val d1 = xDims.getOrElse(t1, 0)
      val d2 = xDims.getOrElse(t2, 0)
      if (d1 > 0 && d2 > 0) {
        val q1 = xValues(t1)
        val q2 = xValues(t2)
        var prev = -1
        for (CrossValue(n1, o1, v1) <- q1) {
          for (CrossValue(n2, o2, v2) <- q2) {
            val offset = d2 * o1 + o2
            fb.skip(offset - prev - 1)
            fb.add(Crossings.name(n1, n2), f(v1, v2))
            prev = offset
          }
        }
        fb.skip(d1 * d2 - prev - 1)
      }
    }
    fb.result
  }

  override def reject(transformer: Transformer[_, _, _], reason: FeatureRejection): Unit =
    fb.reject(transformer, reason)
  override def rejections: Map[String, FeatureRejection] = fb.rejections

  override def newBuilder: FeatureBuilder[F] = CrossingFeatureBuilder(fb, crossings)
}
