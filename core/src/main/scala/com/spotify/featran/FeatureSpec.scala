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

import com.spotify.featran.transformers.{Settings, Transformer}

import scala.collection.mutable
import scala.language.{higherKinds, implicitConversions}

/**
 * Companion object for [[FeatureSpec]].
 */
object FeatureSpec {

  private[featran] type ARRAY = Array[Option[Any]]

  /**
   * Create a new [[FeatureSpec]] for input record type `T`.
   * @tparam T input record type to extract features from
   */
  def of[T]: FeatureSpec[T] = new FeatureSpec[T](Array.empty, Crossings.empty)

  /**
   * Combine multiple [[FeatureSpec]]s into a single spec.
   */
  def combine[T](specs: FeatureSpec[T]*): FeatureSpec[T] = {
    require(specs.nonEmpty, "Empty specs")
    new FeatureSpec(specs.map(_.features).reduce(_ ++ _), specs.map(_.crossings).reduce(_ ++ _))
  }
}

/**
 * Encapsulate specification for feature extraction and transformation.
 * @tparam T input record type to extract features from
 */
class FeatureSpec[T] private[featran] (private[featran] val features: Array[Feature[T, _, _, _]],
                                       private val crossings: Crossings) {

  /**
   * Add a required field specification.
   * @param f function to extract feature `A` from record `T`
   * @param t [[com.spotify.featran.transformers.Transformer Transformer]] for extracted feature `A`
   * @tparam A extracted feature type
   */
  def required[A](f: T => A)(t: Transformer[A, _, _]): FeatureSpec[T] =
    optional(t => Some(f(t)))(t)

  /**
   * Add an optional field specification.
   * @param f function to extract feature `Option[A]` from record `T`
   * @param default default for missing values
   * @param t [[com.spotify.featran.transformers.Transformer Transformer]] for extracted feature `A`
   * @tparam A extracted feature type
   */
  def optional[A](f: T => Option[A], default: Option[A] = None)
                 (t: Transformer[A, _, _]): FeatureSpec[T] =
    new FeatureSpec[T](this.features :+ new Feature(f, default, t), this.crossings)

  /**
   * Cross feature values of two underlying transformers.
   * @param k names of transformers to be crossed
   * @param f function to cross feature value pairs
   */
  def cross(k: (String, String))(f: (Double, Double) => Double): FeatureSpec[T] = {
    val names: Set[String] = features.map(_.transformer.name)(scala.collection.breakOut)
    val d = Set(k._1, k._2).diff(names)
    require(d.isEmpty, s"Feature ${d.mkString(", ")} not found")
    new FeatureSpec[T](this.features, this.crossings + (k -> f))
  }

  /**
   * Extract features from a input collection.
   *
   * This is done in two steps, a `reduce` step over the collection to aggregate feature summary,
   * and a `map` step to transform values using the summary.
   * @param input input collection
   * @tparam M input collection type, e.g. `Array`, `List`
   */
  def extract[M[_]: CollectionType](input: M[T]): FeatureExtractor[M, T] =
    new FeatureExtractor[M, T](new FeatureSet[T](features, crossings), input, None)

  /**
   * Extract features from a input collection using settings from a previous session.
   *
   * This bypasses the `reduce` step in [[extract]] and uses feature summary from settings exported
   * in a previous session.
   * @param input input collection
   * @param settings JSON settings from a previous session
   * @tparam M input collection type, e.g. `Array`, `List`
   */
  def extractWithSettings[M[_]: CollectionType](input: M[T], settings: M[String])
  : FeatureExtractor[M, T] =
    new FeatureExtractor[M, T](new FeatureSet[T](features, crossings), input, Some(settings))

}

private class Feature[T, A, B, C](val f: T => Option[A],
                                  val default: Option[A],
                                  val transformer: Transformer[A, B, C])
  extends Serializable {

  def get(t: T): Option[A] = f(t).orElse(default)

  // Option[A] => Option[B]
  def unsafePrepare(a: Option[Any]): Option[B] =
    a.asInstanceOf[Option[A]].map(transformer.aggregator.prepare)

  // (Option[B], Option[B]) => Option[B]
  def unsafeSum(x: Option[Any], y: Option[Any]): Option[Any] =
    (x.asInstanceOf[Option[B]], y.asInstanceOf[Option[B]]) match {
      case (Some(a), Some(b)) => Some(transformer.aggregator.semigroup.plus(a, b))
      case (Some(a), None) => Some(a)
      case (None, Some(b)) => Some(b)
      case _ => None
    }

  // Option[B] => Option[C]
  def unsafePresent(b: Option[Any]): Option[C] =
    b.asInstanceOf[Option[B]].map(transformer.aggregator.present)

  // Option[C] => Int
  def unsafeFeatureDimension(c: Option[Any]): Int =
    transformer.optFeatureDimension(c.asInstanceOf[Option[C]])

  // Option[C] => Array[String]
  def unsafeFeatureNames(c: Option[Any]): Seq[String] =
    transformer.optFeatureNames(c.asInstanceOf[Option[C]])

  // (Option[A], Option[C], FeatureBuilder[F])
  def unsafeBuildFeatures(a: Option[Any], c: Option[Any], fb: FeatureBuilder[_]): Unit =
    transformer.optBuildFeatures(a.asInstanceOf[Option[A]], c.asInstanceOf[Option[C]], fb)

  // Option[C]
  def unsafeSettings(c: Option[Any]): Settings = transformer.settings(c.asInstanceOf[Option[C]])

  def toIndex(map: Map[String, Int]): Int = map(transformer.name)

}

private class FeatureSet[T](private val features: Array[Feature[T, _, _, _]],
                            private[featran] val crossings: Crossings)
  extends Serializable {

  {
    val (_, dups) = features
      .foldLeft((Set.empty[String], Set.empty[String])) { case ((u, d), f) =>
        val n = f.transformer.name
        if (u.contains(n)) {
          (u, d + n)
        } else {
          (u + n, d)
        }
      }
    require(dups.isEmpty, "duplicate transformer names: " + dups.mkString(", "))
  }

  import FeatureSpec.ARRAY

  private val n = features.length

  // T => Array[Option[A]]
  def unsafeGet(t: T): ARRAY = features.map(_.get(t))

  // Array[Option[A]] => Array[Option[B]]
  def unsafePrepare(a: ARRAY): ARRAY = {
    require(n == a.length)
    var i = 0
    val r = Array.fill[Option[Any]](n)(null)
    while (i < n) {
      r(i) = features(i).unsafePrepare(a(i))
      i += 1
    }
    r
  }

  // (Array[Option[B]], Array[Option[B]]) => Array[Option[B]]
  def unsafeSum(lhs: ARRAY, rhs: ARRAY): ARRAY = {
    require(n == lhs.length)
    require(n == rhs.length)
    val r = Array.fill[Option[Any]](n)(null)
    var i = 0
    while (i < n) {
      r(i) = features(i).unsafeSum(lhs(i), rhs(i))
      i += 1
    }
    r
  }

  // Array[Option[B]] => Array[Option[C]]
  def unsafePresent(b: ARRAY): ARRAY = {
    require(n == b.length)
    var i = 0
    val r = Array.fill[Option[Any]](n)(null)
    while (i < n) {
      r(i) = features(i).unsafePresent(b(i))
      i += 1
    }
    r
  }

  // Array[Option[C]] => Int
  def featureDimension(c: ARRAY): Int = {
    require(n == c.length)
    var sum = 0
    var i = 0
    val m = mutable.Map.empty[String, Int]
    while (i < n) {
      val f = features(i)
      val size = f.unsafeFeatureDimension(c(i))
      sum += size
      if (crossings.keys.contains(f.transformer.name)) {
        m(f.transformer.name) = size
      }
      i += 1
    }
    crossings.map.keys.foreach { case (n1, n2) =>
      sum += m(n1) * m(n2)
    }
    sum
  }

  // Array[Option[C]] => Array[String]
  def featureNames(c: ARRAY): Seq[String] = {
    require(n == c.length)
    val b = Seq.newBuilder[String]
    var i = 0
    val m = mutable.Map.empty[String, Seq[String]]
    while (i < n) {
      val f = features(i)
      val names = f.unsafeFeatureNames(c(i))
      b ++= names
      if (crossings.keys.contains(f.transformer.name)) {
        m(f.transformer.name) = names
      }
      i += 1
    }
    crossings.map.keys.foreach { case (n1, n2) =>
      for (x <- m(n1); y <- m(n2)) {
        b += Crossings.name(x, y)
      }
    }
    b.result()
  }

  // (Array[Option[A]], Array[Option[C]], FeatureBuilder[F])
  def featureValues[F](a: ARRAY, c: ARRAY, fb: FeatureBuilder[F]): Unit = {
    require(n == c.length)
    fb.init(featureDimension(c))
    var i = 0
    while (i < n) {
      val f = features(i)
      fb.prepare(f.transformer)
      f.unsafeBuildFeatures(a(i), c(i), fb)
      i += 1
    }
  }

  // Option[C]
  def featureSettings(c: ARRAY): Seq[Settings] = {
    require(n == c.length)
    val b = Seq.newBuilder[Settings]
    var i = 0
    while (i < n) {
      b += features(i).unsafeSettings(c(i))
      i += 1
    }
    b.result()
  }

  def decodeAggregators(s: Seq[Settings]): ARRAY = {
    val m: Map[String, Settings] = s.map(x => (x.name, x))(scala.collection.breakOut)
    features.map { feature =>
      val name = feature.transformer.name
      require(m.contains(name), s"Missing settings for $name")
      feature.transformer.decodeAggregator(m(feature.transformer.name).aggregators)
    }
  }

  //================================================================================
  // For MultiFeatureSpec and MultiFeatureExtractor
  //================================================================================

  // Array[Option[C]] => Array[String]
  def multiFeatureNames(c: ARRAY, dims: Int, mapping: Map[String, Int]): Seq[Seq[String]] = {
    require(n == c.length)
    val b = 0.until(dims).map(_ => Seq.newBuilder[String])
    var i = 0
    while (i < n) {
      val feature = features(i)
      val idx = feature.toIndex(mapping)
      feature.unsafeFeatureNames(c(i)).foreach(b(idx) += _)
      i += 1
    }
    b.map(_.result())
  }

  // Array[Option[C]] => Array[Int]
  def multiFeatureDimension(c: ARRAY, dims: Int, mapping: Map[String, Int]): Array[Int] = {
    val featureCount = Array.fill[Int](dims)(0)
    var i = 0
    while (i < n) {
      val f = features(i)
      val idx = f.toIndex(mapping)
      featureCount(idx) += features(i).unsafeFeatureDimension(c(i))
      i += 1
    }
    featureCount
  }

  // (Array[Option[A]], Array[Option[C]], FeatureBuilder[F])
  def multiFeatureValues[F](a: ARRAY, c: ARRAY, fbs: Array[FeatureBuilder[F]],
                            dims: Int, mapping: Map[String, Int]): Unit = {

    var i = 0
    val counts = multiFeatureDimension(c, dims, mapping)
    while (i < fbs.length) {
      fbs(i).init(counts(i))
      i += 1
    }

    i = 0
    while (i < n) {
      val f = features(i)
      val fb = fbs(mapping(f.transformer.name))
      f.unsafeBuildFeatures(a(i), c(i), fb)
      i += 1
    }
  }

}
