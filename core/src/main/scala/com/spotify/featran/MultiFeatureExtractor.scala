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

import java.io._
import scala.reflect.ClassTag
import scala.language.{higherKinds, implicitConversions}

/**
 * Encapsulate features extracted from a [[MultiFeatureSpec]].
 * Allows seperation back into specs by names or vector
 * @tparam M input collection type, e.g. `Array`, List
 * @tparam T input record type to extract features from
 */
class MultiFeatureExtractor[M[_]: CollectionType, T] private[featran]
(private val fs: FeatureSet[T],
 @transient private val input: M[T],
 @transient private val settings: Option[M[String]],
 @transient private val mapping: Map[String, Int])
  extends Serializable {

  private val extractor = new FeatureExtractor(fs, input, settings)

  private lazy val dims: Int = mapping.values.toSet.size

  @transient private val dt: CollectionType[M] = implicitly[CollectionType[M]]
  import dt.Ops._

  def featureValues[F: FeatureBuilder : ClassTag]: M[Seq[F]] =
    featureValuesWithOriginal.map(_._1)

  def featureValuesWithOriginal[F: FeatureBuilder : ClassTag]: M[(Seq[F], T)] = {
    val fb = implicitly[FeatureBuilder[F]]
    val buffer = new ByteArrayOutputStream()
    val out = new ObjectOutputStream(buffer)
    out.writeObject(fb)
    val bytes = buffer.toByteArray
    val fbs = Array.fill(dims) {
      val in = new ObjectInputStream(new ByteArrayInputStream(bytes))
      in.readObject().asInstanceOf[FeatureBuilder[F]]
    }
    extractor.as.cross(extractor.aggregate).map { case ((o, a), c) =>
      fs.multiFeatureValues(a, c, fbs, mapping)
      (fbs.map(_.result).toSeq, o)
    }
  }

  @transient lazy val featureNames: M[Seq[Seq[String]]] =
    extractor.aggregate.map(a => fs.multiFeatureNames(a, mapping))

  @transient lazy val featureSettings: M[String] = extractor.featureSettings
}
