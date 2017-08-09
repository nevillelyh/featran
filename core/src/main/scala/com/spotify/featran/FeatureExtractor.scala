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

import com.spotify.featran.transformers.Settings

import scala.language.{higherKinds, implicitConversions}
import scala.reflect.ClassTag

/**
 * Encapsulate features extracted from a [[FeatureSpec]].
 * @tparam M input collection type, e.g. `Array`, List
 * @tparam T input record type to extract features from
 */
class FeatureExtractor[M[_]: CollectionType, T] private[featran]
(private val fs: FeatureSet[T],
 @transient private val input: M[T],
 @transient private val settings: Option[M[String]])
  extends Serializable {

  import FeatureSpec.ARRAY

  @transient private val dt: CollectionType[M] = implicitly[CollectionType[M]]
  import dt.Ops._

  @transient private[featran] lazy val as: M[(T, ARRAY)] = {
    val g = fs // defeat closure
    input.map(o => (o, g.unsafeGet(o)))
  }
  @transient private[featran] lazy val aggregate: M[ARRAY] = settings match {
    case Some(x) => x.map { s =>
      import io.circe.generic.auto._
      import io.circe.parser._
      fs.decodeAggregators(decode[Seq[Settings]](s).right.get)
    }
    case None =>
      as
        .map(t => fs.unsafePrepare(t._2))
        .reduce(fs.unsafeSum)
        .map(fs.unsafePresent)
  }

  /**
   * JSON settings of the [[FeatureSpec]] and aggregated feature summary.
   *
   * This can be used with [[FeatureSpec.extractWithSettings]] to bypass the `reduce` step when
   * extracting new records of the same type.
   */
  @transient lazy val featureSettings: M[String] = settings match {
    case Some(x) => x
    case None => aggregate.map { a =>
      import io.circe.generic.auto._
      import io.circe.syntax._
      fs.featureSettings(a).asJson.noSpaces
    }
  }

  /**
   * Names of the extracted features, in the same order as values in [[featureValues]].
   */
  @transient lazy val featureNames: M[Seq[String]] = aggregate.map(fs.featureNames)

  /**
   * Values of the extracted features, in the same order as names in [[featureNames]].
   * @tparam F output data type, e.g. `Array[Float]`, `Array[Double]`, `DenseVector[Float]`,
   *           `DenseVector[Double]`
   */
  def featureValues[F: FeatureBuilder : ClassTag]: M[F] =
    featureValuesWithOriginal.map(_._1)

  /**
   * Values of the extracted features, in the same order as names in [[featureNames]] with the
   * original input record.
   * @tparam F output data type, e.g. `Array[Float]`, `Array[Double]`, `DenseVector[Float]`,
   *           `DenseVector[Double]`
   */
  def featureValuesWithOriginal[F: FeatureBuilder : ClassTag]: M[(F, T)] = {
    val fb = implicitly[FeatureBuilder[F]]
    as.cross(aggregate).map { case ((o, a), c) =>
      fs.featureValues(a, c, fb)
      (fb.result, o)
    }
  }

}
