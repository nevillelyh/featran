package com.spotify.featran.transformers

import com.spotify.featran.FeatureSpec
import org.scalacheck.{Arbitrary, Prop}

object SegmentIndicesSpec extends TransformerProp("SegmentIndices") {

  implicit lazy val randomIncreasingArray: Arbitrary[Array[Int]] = Arbitrary {
      val increasingArray = Array.fill(10)(0)
      for (index <- 1 until increasingArray.length) {
        if (math.random() > 0.5) {
          increasingArray(index) = increasingArray(index - 1) + 1
        } else
          increasingArray(index) = increasingArray(index - 1)
      }
      increasingArray
  }

  property("default") = Prop.forAll { (xs: List[Array[Int]]) =>

    val segmentIndicesSpec = FeatureSpec
      .of[Array[Int]]
      .required(identity)(SegmentIndices("segmented"))

    val expected = xs.map { testCase =>
      testCase.groupBy(identity).toSeq.sortBy(_._1).flatMap { case (_, sameNumber) =>
        sameNumber.indices.toList }
    }

    val result = segmentIndicesSpec.extract(xs)
      .featureValues[Array[Int]]
      .map(_.toSeq)

    Prop.all(result == expected)
  }
}
