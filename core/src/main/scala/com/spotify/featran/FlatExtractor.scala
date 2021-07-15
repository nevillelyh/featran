/*
 * Copyright 2018 Spotify AB.
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

import com.spotify.featran.transformers._
import simulacrum.typeclass

import scala.reflect.ClassTag
import scala.annotation.implicitNotFound

/**
 * TypeClass that is used to read data from flat files.  The requirement is that each
 * feature comes from the same type and can be looked up by name.
 * @tparam T The intermediate storage format for each feature.
 */
@implicitNotFound("Could not find an instance of FlatReader for ${T}")
@typeclass trait FlatReader[T] extends Serializable {
  def readDouble(name: String): T => Option[Double]

  def readMdlRecord(name: String): T => Option[MDLRecord[String]]

  def readWeightedLabel(name: String): T => Option[List[WeightedLabel]]

  def readDoubles(name: String): T => Option[Seq[Double]]

  def readDoubleArray(name: String): T => Option[Array[Double]]

  def readString(name: String): T => Option[String]

  def readStrings(name: String): T => Option[Seq[String]]

  def readIntArray(name: String): T => Option[Array[Int]]
}

object FlatReader {
  /* ======================================================================== */
  /* THE FOLLOWING CODE IS MANAGED BY SIMULACRUM; PLEASE DO NOT EDIT!!!!      */
  /* ======================================================================== */

  /** Summon an instance of [[FlatReader]] for `T`. */
  @inline def apply[T](implicit instance: FlatReader[T]): FlatReader[T] = instance

  object ops {
    implicit def toAllFlatReaderOps[T](target: T)(implicit tc: FlatReader[T]): AllOps[T] {
      type TypeClassType = FlatReader[T]
    } = new AllOps[T] {
      type TypeClassType = FlatReader[T]
      val self: T = target
      val typeClassInstance: TypeClassType = tc
    }
  }
  trait Ops[T] extends Serializable {
    type TypeClassType <: FlatReader[T]
    def self: T
    val typeClassInstance: TypeClassType
  }
  trait AllOps[T] extends Ops[T]
  trait ToFlatReaderOps extends Serializable {
    implicit def toFlatReaderOps[T](target: T)(implicit tc: FlatReader[T]): Ops[T] {
      type TypeClassType = FlatReader[T]
    } = new Ops[T] {
      type TypeClassType = FlatReader[T]
      val self: T = target
      val typeClassInstance: TypeClassType = tc
    }
  }
  object nonInheritedOps extends ToFlatReaderOps

  /* ======================================================================== */
  /* END OF SIMULACRUM-MANAGED CODE                                           */
  /* ======================================================================== */

}

/**
 * Sometimes it is useful to store the features in an intermediate state in normally
 * a flat version like Examples or maybe JSON.  This makes it easier to interface with
 * other systems.
 */
object FlatExtractor {

  /**
   * This function allows the reading of data from these flat versions by name with a given
   * settings file to extract the final output.
   *
   * @param settings Setting information
   * @tparam M Collection Type
   * @tparam T The intermediate format where the data is stored
   * @return Class for converting to Features
   */
  @inline def apply[M[_]: CollectionType, T: ClassTag: FlatReader](
    settings: M[String]
  ): FlatExtractor[M, T] = new FlatExtractor[M, T](settings)

  /**
   * Another useful operation is to use the Spec and Information we have to map from
   * the Scala Object to read the data directly from the intermediate format for parsing
   *
   * @param spec Current FeatureSpec
   * @tparam T The intermediate format where the data is stored
   * @tparam X The Input Scala Object
   * @return FeatureSpec for the intermediate format
   */
  def flatSpec[T: ClassTag: FlatReader, X: ClassTag](spec: FeatureSpec[X]): FeatureSpec[T] = {
    val features = spec.features.map { feature =>
      val t = feature.transformer.asInstanceOf[Transformer[Any, _, _]]
      new Feature(feature.transformer.flatRead, feature.default, t)
        .asInstanceOf[Feature[T, _, _, _]]
    }
    new FeatureSpec[T](features, spec.crossings)
  }

  /**
   * Another useful operation is to use the Spec and Information we have to map from
   * the Scala Object to read the data directly from the intermediate format for parsing
   *
   * @param spec Current FeatureSpec
   * @tparam T The intermediate format where the data is stored
   * @tparam X The Input Scala Object
   * @return FeatureSpec for the intermediate format
   */
  def multiFlatSpec[T: ClassTag: FlatReader, X: ClassTag](
    spec: MultiFeatureSpec[X]
  ): MultiFeatureSpec[T] = {
    val features = spec.features.map { feature =>
      val t = feature.transformer.asInstanceOf[Transformer[Any, _, _]]
      new Feature(feature.transformer.flatRead, feature.default, t)
        .asInstanceOf[Feature[T, _, _, _]]
    }
    new MultiFeatureSpec[T](spec.mapping, features, spec.crossings)
  }
}

private[featran] class FlatExtractor[M[_]: CollectionType, T: ClassTag: FlatReader](
  settings: M[String]
) extends Serializable {
  import json._
  import CollectionType.ops._
  import scala.reflect.runtime.universe

  @transient private[this] val converters = settings.map { str =>
    val runtimeMirror = universe.runtimeMirror(getClass.getClassLoader)
    val jsonOpt = decode[Seq[Settings]](str)
    assert(jsonOpt.isRight, "Unable to parse the settings files.")
    jsonOpt.right.get.map { setting =>
      val transformer = runtimeMirror
        .reflectModule(runtimeMirror.staticModule(setting.cls))
        .instance
        .asInstanceOf[SettingsBuilder]
        .fromSettings(setting)

      (transformer.flatRead[T], setting.aggregators, transformer)
    }
  }

  @transient private[this] val dimSize: M[Int] = converters.map { items =>
    items.map { case (_, aggr, tr) =>
      val ta = aggr.map(tr.decodeAggregator)
      tr.unsafeFeatureDimension(ta)
    }.sum
  }

  def featureValues[F: FeatureBuilder: ClassTag](records: M[T]): M[F] =
    featureResults(records).map(_._1)

  def featureResults[F: FeatureBuilder: ClassTag](
    records: M[T]
  ): M[(F, Map[String, FeatureRejection])] = {
    val fb = FeatureBuilder[F].newBuilder
    records.cross(converters).cross(dimSize).map { case ((record, convs), size) =>
      fb.init(size)
      convs.foreach { case (fn, aggr, conv) =>
        val a = aggr.map(conv.decodeAggregator)
        conv.unsafeBuildFeatures(fn(record), a, fb)
      }
      (fb.result, fb.rejections)
    }
  }
}
