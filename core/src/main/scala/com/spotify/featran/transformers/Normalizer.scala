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

package com.spotify.featran.transformers

import breeze.linalg._
import com.spotify.featran.{FeatureBuilder, FeatureRejection, FlatReader, FlatWriter}
import com.twitter.algebird.Aggregator

/**
 * Transform vector features by normalizing each vector to have unit norm. Parameter `p` specifies
 * the p-norm used for normalization (default 2).
 *
 * Missing values are transformed to zero vectors.
 *
 * When using aggregated feature summary from a previous session, vectors of different dimensions
 * are transformed to zero vectors and [[FeatureRejection.WrongDimension]] rejections are reported.
 */
object Normalizer extends SettingsBuilder {

  /**
   * Create a new [[Normalizer]] instance.
   * @param p normalization in L^p^ space, must be greater than or equal to 1.0
   * @param expectedLength expected length of the input vectors, or 0 to infer from data
   */
  def apply(
    name: String,
    p: Double = 2.0,
    expectedLength: Int = 0
  ): Transformer[Array[Double], Int, Int] =
    new Normalizer(name, p, expectedLength)

  /**
   * Create a new [[Normalizer]] from a settings object
   * @param setting Settings object
   */
  def fromSettings(setting: Settings): Transformer[Array[Double], Int, Int] = {
    val p = setting.params("p").toDouble
    val expectedLength = setting.params("expectedLength").toInt
    Normalizer(setting.name, p, expectedLength)
  }
}

private[featran] class Normalizer(name: String, val p: Double, val expectedLength: Int)
    extends Transformer[Array[Double], Int, Int](name) {
  require(p >= 1.0, "p must be >= 1.0")
  override val aggregator: Aggregator[Array[Double], Int, Int] =
    Aggregators.seqLength(expectedLength)
  override def featureDimension(c: Int): Int = c
  override def featureNames(c: Int): Seq[String] = names(c)
  override def buildFeatures(a: Option[Array[Double]], c: Int, fb: FeatureBuilder[_]): Unit =
    a match {
      case Some(x) =>
        if (x.length != c) {
          fb.skip(c)
          fb.reject(this, FeatureRejection.WrongDimension(c, x.length))
        } else {
          val dv = DenseVector(x)
          fb.add(names(c), (dv / norm(dv, p))(DenseVector.dv_s_Op_Double_OpDiv).data)
        }
      case None => fb.skip(c)
    }
  override def encodeAggregator(c: Int): String = c.toString
  override def decodeAggregator(s: String): Int = s.toInt
  override def params: Map[String, String] =
    Map("p" -> p.toString, "expectedLength" -> expectedLength.toString)

  override def flatRead[T: FlatReader]: T => Option[Any] = FlatReader[T].readDoubleArray(name)

  override def flatWriter[T](implicit fw: FlatWriter[T]): Option[Array[Double]] => fw.IF =
    fw.writeDoubleArray(name)
}
