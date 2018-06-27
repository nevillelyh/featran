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

import com.google.protobuf.ByteString
import com.spotify.featran.transformers.{ConvertFunction, Converter, WeightedLabel}
import org.tensorflow.example._
import org.tensorflow.{example => tf}

import scala.reflect.runtime.universe._
import scala.collection.JavaConverters._

case class NamedTFFeature(name: String, f: tf.Feature)

package object tensorflow {
  case class TensorFlowFeatureBuilder(
    @transient private var underlying: Features.Builder = tf.Features.newBuilder())
      extends FeatureBuilder[tf.Example] {
    override def init(dimension: Int): Unit = {
      if (underlying == null) {
        underlying = tf.Features.newBuilder()
      }
      underlying.clear()
    }
    override def add(name: String, value: Double): Unit = {
      val feature = tf.Feature
        .newBuilder()
        .setFloatList(tf.FloatList.newBuilder().addValue(value.toFloat))
        .build()
      val normalized = name.replaceAll("[^A-Za-z0-9_]", "_")
      underlying.putFeature(normalized, feature)
    }
    override def skip(): Unit = Unit
    override def skip(n: Int): Unit = Unit
    override def result: tf.Example =
      tf.Example.newBuilder().setFeatures(underlying).build()

    override def newBuilder: FeatureBuilder[Example] = TensorFlowFeatureBuilder()
  }

  /**
   * [[FeatureBuilder]] for output as TensorFlow `Example` type.
   */
  implicit def tensorFlowFeatureBuilder: FeatureBuilder[tf.Example] = TensorFlowFeatureBuilder()

  // scalastyle:off
  implicit val converterFunction = new ConvertFunction[List[NamedTFFeature]] {
    def apply[T: TypeTag](name: String, v: T): List[NamedTFFeature] = {
      typeOf[T] match {
        case t if t =:= typeOf[Double] =>
          val f = Feature
            .newBuilder()
            .setFloatList(
              FloatList
                .newBuilder()
                .addAllValue(Seq(float2Float(v.asInstanceOf[Double].toFloat)).asJava)
            )
            .build()
          List(NamedTFFeature(name, f))

        case t if t =:= typeOf[String] =>
          val str = v.asInstanceOf[String]
          val f = Feature
            .newBuilder()
            .setBytesList(BytesList.newBuilder().addValue(ByteString.copyFromUtf8(str)))
            .build()

          List(NamedTFFeature(name, f))

        case t if t <:< typeOf[Seq[String]] =>
          v.asInstanceOf[Seq[String]].toList.map { category =>
            val f = Feature
              .newBuilder()
              .setBytesList(BytesList.newBuilder().addValue(ByteString.copyFromUtf8(category)))
              .build()

            NamedTFFeature(name, f)
          }

        case t if t <:< typeOf[Seq[Double]] =>
          val values = v.asInstanceOf[Seq[Double]].map(v => float2Float(v.toFloat))
          val f = Feature
            .newBuilder()
            .setFloatList(
              FloatList
                .newBuilder()
                .addAllValue(values.asJava)
            )
            .build()

          List(NamedTFFeature(name, f))

        case t if t <:< typeOf[Seq[WeightedLabel]] =>
          val values = v.asInstanceOf[Seq[WeightedLabel]]

          val strs = values.map(v => ByteString.copyFromUtf8(v.name))

          val kfeature = Feature
            .newBuilder()
            .setBytesList(BytesList.newBuilder().addAllValue(strs.asJava))
            .build()

          val vfeature = Feature
            .newBuilder()
            .setFloatList(
              FloatList
                .newBuilder()
                .addAllValue(values.map(v => float2Float(v.value.toFloat)).asJava))
            .build()

          List(NamedTFFeature(name + "_key", kfeature), NamedTFFeature(name + "_key", vfeature))

        case _ => Nil
      }
    }
  }

  implicit val tfConverter = new Converter[tf.Example] {
    def convert[T](row: T, feat: List[Feature[T, _, _, _]]): tf.Example = {
      val builder = Features.newBuilder()
      feat.foreach { f =>
        f.convert[List[NamedTFFeature]](row).foreach { opt =>
          opt.foreach { nf =>
            builder.putFeature(nf.name, nf.f)
          }
        }
      }
      Example
        .newBuilder()
        .setFeatures(builder.build())
        .build()
    }
  }
}
