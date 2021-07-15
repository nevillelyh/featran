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

import simulacrum.typeclass

import scala.annotation.implicitNotFound

/** Type class for floating point primitives. */
@implicitNotFound("Could not find an instance of FloatingPoint for ${T}")
@typeclass trait FloatingPoint[@specialized(Float, Double, Int) T] extends Serializable {
  def fromDouble(x: Double): T
}

object FloatingPoint {
  implicit val floatFP: FloatingPoint[Float] = new FloatingPoint[Float] {
    override def fromDouble(x: Double): Float = x.toFloat
  }
  implicit val doubleFP: FloatingPoint[Double] = new FloatingPoint[Double] {
    override def fromDouble(x: Double): Double = x
  }
  implicit val intFP: FloatingPoint[Int] = new FloatingPoint[Int] {
    override def fromDouble(x: Double): Int = x.toInt
  }

  /* ======================================================================== */
  /* THE FOLLOWING CODE IS MANAGED BY SIMULACRUM; PLEASE DO NOT EDIT!!!!      */
  /* ======================================================================== */

  /** Summon an instance of [[FloatingPoint]] for `T`. */
  @inline def apply[T](implicit instance: FloatingPoint[T]): FloatingPoint[T] = instance

  object ops {
    implicit def toAllFloatingPointOps[T](target: T)(implicit tc: FloatingPoint[T]): AllOps[T] {
      type TypeClassType = FloatingPoint[T]
    } = new AllOps[T] {
      type TypeClassType = FloatingPoint[T]
      val self: T = target
      val typeClassInstance: TypeClassType = tc
    }
  }
  trait Ops[@specialized(Float, Double) T] extends Serializable {
    type TypeClassType <: FloatingPoint[T]
    def self: T
    val typeClassInstance: TypeClassType
  }
  trait AllOps[@specialized(Float, Double) T] extends Ops[T]
  trait ToFloatingPointOps extends Serializable {
    implicit def toFloatingPointOps[T](target: T)(implicit tc: FloatingPoint[T]): Ops[T] {
      type TypeClassType = FloatingPoint[T]
    } = new Ops[T] {
      type TypeClassType = FloatingPoint[T]
      val self: T = target
      val typeClassInstance: TypeClassType = tc
    }
  }
  object nonInheritedOps extends ToFloatingPointOps

  /* ======================================================================== */
  /* END OF SIMULACRUM-MANAGED CODE                                           */
  /* ======================================================================== */

}
