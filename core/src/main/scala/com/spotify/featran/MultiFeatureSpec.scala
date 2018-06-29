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

import com.spotify.featran.transformers.{ConvertFns, Converter, Settings}

import scala.language.{higherKinds, implicitConversions}
import scala.reflect.ClassTag
import scala.collection.breakOut

/**
 * Companion object for [[MultiFeatureSpec]].
 */
object MultiFeatureSpec {
  def apply[T](specs: FeatureSpec[T]*): MultiFeatureSpec[T] = {
    val nameToSpec: Map[String, Int] = specs.zipWithIndex.flatMap {
      case (spec, index) =>
        spec.features.map(_.transformer.name -> index)
    }(breakOut)

    new MultiFeatureSpec(nameToSpec,
                         specs.map(_.features).reduce(_ ++ _),
                         specs.map(_.crossings).reduce(_ ++ _))
  }
}

/**
 * Wrapper for [[FeatureSpec]] that allows for combination and separation of different specs.
 */
class MultiFeatureSpec[T](private[featran] val mapping: Map[String, Int],
                          private[featran] val features: Array[Feature[T, _, _, _]],
                          private val crossings: Crossings) {

  private def multiFeatureSet: MultiFeatureSet[T] =
    new MultiFeatureSet[T](features, crossings, mapping)

  /**
   * Uses the functions provided in the Featran Spec to `map` the scala object into a new type
   * given by C.  This function does not do the transformations internal to Featran but instead
   * can be used as a mapper.
   *
   * For an example of this see the tensorflow subproject where mapping between Scala and TFExample
   * can be done through this method.
   */
  def convert[M[_], C](
    input: M[T])(implicit fw: Converter[C], ct: ClassTag[C], dt: CollectionType[M]): M[C] = {
    import CollectionType.ops._
    val fns = ConvertFns(features.map(f => fw(f.transformer.name, f.typ, f.f)).toList)
    input.map { row =>
      fw.convert(row, fns)
    }
  }

  /**
   * Extract features from a input collection.
   *
   * This is done in two steps, a `reduce` step over the collection to aggregate feature summary,
   * and a `map` step to transform values using the summary.
   *
   * @param input input collection
   * @tparam M input collection type, e.g. `Array`, `List`
   */
  def extract[M[_]: CollectionType](input: M[T]): MultiFeatureExtractor[M, T] = {
    import CollectionType.ops._

    val fs = input.pure(multiFeatureSet)
    new MultiFeatureExtractor[M, T](fs, input, None)
  }

  /**
   * Creates a new MultiFeatureSpec with only the features that respect the given predicate.
   *
   * @param predicate Function determining whether or not to include the feature
   */
  def filter(predicate: Feature[T, _, _, _] => Boolean): MultiFeatureSpec[T] = {
    val filteredFeatures = features.filter(predicate)
    val featuresByName =
      filteredFeatures.map[(String, Feature[T, _, _, _]), Map[String, Feature[T, _, _, _]]](f =>
        f.transformer.name -> f)(breakOut)

    val filteredMapping = mapping.filterKeys(featuresByName.contains)
    val filteredCrossings = crossings.filter(featuresByName.contains)

    new MultiFeatureSpec[T](filteredMapping, filteredFeatures, filteredCrossings)
  }

  /**
   * Extract features from a input collection using settings from a previous session.
   *
   * This bypasses the `reduce` step in [[extract]] and uses feature summary from settings exported
   * in a previous session.
   * @param input input collection
   * @param settings JSON settings from a previous session
   * @tparam M input collection type, e.g. `Array`, `List`
   */
  def extractWithSettings[M[_]: CollectionType](
    input: M[T],
    settings: M[String]): MultiFeatureExtractor[M, T] = {
    import CollectionType.ops._

    val fs = input.pure(multiFeatureSet)
    new MultiFeatureExtractor[M, T](fs, input, Some(settings))
  }

  /**
   * Extract features from a input collection using partial settings from a previous session.
   *
   * This bypasses the `reduce` step in [[extract]] and uses feature summary from settings exported
   * in a previous session.
   * @param input input collection
   * @param settings JSON settings from a previous session
   * @tparam M input collection type, e.g. `Array`, `List`
   */
  def extractWithSubsetSettings[M[_]: CollectionType](
    input: M[T],
    settings: M[String]): MultiFeatureExtractor[M, T] = {
    import CollectionType.ops._

    val featureSet = settings.map { s =>
      val settingsJson = JsonSerializable[Seq[Settings]].decode(s).right.get
      val predicate: Feature[T, _, _, _] => Boolean =
        f => settingsJson.exists(x => x.name == f.transformer.name)

      filter(predicate).multiFeatureSet
    }

    new MultiFeatureExtractor[M, T](featureSet, input, Some(settings))
  }

}
