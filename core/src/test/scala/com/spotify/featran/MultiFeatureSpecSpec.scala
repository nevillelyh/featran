package com.spotify.featran

import breeze.linalg.{DenseVector, SparseVector}
import com.spotify.featran.transformers.Identity
import org.scalacheck.{Arbitrary, Gen, Prop, Properties}

class MultiFeatureSpecSpec extends Properties("MultiFeatureSpec") {
  case class Record(d: Double, optD: Option[Double])

  implicit val arbRecords: Arbitrary[List[Record]] = Arbitrary {
    Gen.listOfN(100, Arbitrary.arbitrary[(Double, Option[Double])].map(Record.tupled))
  }

  private val id = Identity("id")
  private val id2 = Identity("id2")

  private val f = FeatureSpec.of[Record].required(_.d)(id)
  private val f2 = FeatureSpec.of[Record].required(_.d)(id2)

  property("multi feature extraction") = Prop.forAll { xs: List[Record] =>
    val multi = MultiFeatureSpec.specs(f, f2).extract(xs)
    Prop.all(
      multi.featureNames == Seq(Seq(Seq("id"), Seq("id2"))),
      multi.featureValues[Seq[Double]] == xs.map(r => Seq(Seq(r.d), Seq(r.d)))
    )
  }

  property("multi feature extraction map") = Prop.forAll { xs: List[Record] =>
    val multi = MultiFeatureSpec.specs(f, f2).extract(xs)
    val expected = xs.map(r => Seq(Map("id" -> r.d), Map("id2" -> r.d)))
    Prop.all(
      multi.featureNames == Seq(Seq(Seq("id"), Seq("id2"))),
      multi.featureValues[Map[String, Double]] == expected)
  }

  property("multi feature extraction sparse") = Prop.forAll { xs: List[Record] =>
    val multi = MultiFeatureSpec.specs(f, f2).extract(xs)
    val expected = xs.map(r => Seq(SparseVector(1)((0, r.d)), SparseVector(1)((0, r.d))))
    Prop.all(
      multi.featureNames == Seq(Seq(Seq("id"), Seq("id2"))),
      multi.featureValues[SparseVector[Double]] == expected
    )
  }

  property("multi feature extraction dense") = Prop.forAll { xs: List[Record] =>
    val multi = MultiFeatureSpec.specs(f, f2).extract(xs)
    val expected = xs.map(r => Seq(DenseVector(r.d), DenseVector(r.d)))
    Prop.all(
      multi.featureNames == Seq(Seq(Seq("id"), Seq("id2"))),
      multi.featureValues[DenseVector[Double]] == expected
    )
  }

  property("multi feature extraction settings") = Prop.forAll { xs: List[Record] =>
    val settings = MultiFeatureSpec.specs(f, f2).extract(xs).featureSettings
    val multi = MultiFeatureSpec.specs(f, f2).extractWithSettings(xs, settings)
    Prop.all(
      multi.featureNames == Seq(Seq(Seq("id"), Seq("id2"))),
      multi.featureValues[Seq[Double]] == xs.map(r => Seq(Seq(r.d), Seq(r.d)))
    )
  }
}
