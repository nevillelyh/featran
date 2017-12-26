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

package com.spotify.featran.transformers.mdl

import org.scalactic.TolerantNumerics

import scala.io.Source

case class CarRecord(
  mpg: Double,
  cylinders: Int,
  cubicinches: Int,
  horsepower: Double,
  weightlbs: Double,
  timeToSixty: Double,
  year: Int,
  brand: String,
  origin: String
)

object TestUtility {
  implicit val doubleEquality = TolerantNumerics.tolerantDoubleEquality(0.01)

  def readCars: List[CarRecord] = {
    Source
      .fromFile("core/src/test/resources/cars.data")
      .getLines
      .toList
      .map{line =>
        val x = line.split(",").map(elem => elem.trim)
        CarRecord(
          x(0).toDouble,
          x(1).toInt,
          x(2).toInt,
          x(3).toDouble,
          x(4).toDouble,
          x(5).toDouble,
          x(6).toInt,
          x(7),
          x(8)
        )}
  }

  val origin = Map(
    "Japan" -> 0,
    "US" -> 1,
    "Europe" -> 2
  )

  def idxFirstNoneZero(list: List[Double]): Int =
    list.zipWithIndex.find(_._1 == 1.0).get._2
}
