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

import scala.collection.mutable
import scala.reflect.ClassTag

// Workaround for CanBuildFrom not serializable
trait CanBuild[T, M[_]] extends Serializable {
  def apply(): mutable.Builder[T, M[T]]
}

object CanBuild {
  // Collection types in _root_.scala.*
  implicit def iterableCB[T]: CanBuild[T, Iterable] = new CanBuild[T, Iterable] {
    override def apply(): mutable.Builder[T, Iterable[T]] = Iterable.newBuilder
  }

  implicit def seqCB[T]: CanBuild[T, Seq] = new CanBuild[T, Seq] {
    override def apply(): mutable.Builder[T, Seq[T]] = Seq.newBuilder
  }

  implicit def indexedSeqCB[T]: CanBuild[T, IndexedSeq] = new CanBuild[T, IndexedSeq] {
    override def apply(): mutable.Builder[T, IndexedSeq[T]] = IndexedSeq.newBuilder
  }

  implicit def listCB[T]: CanBuild[T, List] = new CanBuild[T, List] {
    override def apply(): mutable.Builder[T, List[T]] = List.newBuilder
  }

  implicit def vectorCB[T]: CanBuild[T, Vector] = new CanBuild[T, Vector] {
    override def apply(): mutable.Builder[T, Vector[T]] = Vector.newBuilder
  }

  implicit def bufferCB[T]: CanBuild[T, mutable.Buffer] = new CanBuild[T, mutable.Buffer] {
    override def apply(): mutable.Builder[T, mutable.Buffer[T]] = mutable.Buffer.newBuilder
  }

  implicit def floatArrayCB: CanBuild[Float, Array] = new CanBuild[Float, Array] {
    override def apply(): mutable.Builder[Float, Array[Float]] = Array.newBuilder[Float]
  }

  implicit def doubleArrayCB: CanBuild[Double, Array] = new CanBuild[Double, Array] {
    override def apply(): mutable.Builder[Double, Array[Double]] = Array.newBuilder[Double]
  }

  implicit def arrayCB[T: ClassTag]: CanBuild[T, Array] = new CanBuild[T, Array] {
    override def apply(): mutable.Builder[T, Array[T]] = Array.newBuilder[T]
  }

  implicit def intArrayCB: CanBuild[Int, Array] = new CanBuild[Int, Array] {
    override def apply(): mutable.Builder[Int, Array[Int]] = Array.newBuilder[Int]
  }
}
