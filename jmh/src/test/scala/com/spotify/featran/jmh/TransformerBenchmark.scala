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

package com.spotify.featran.jmh

import java.util.concurrent.TimeUnit

import com.spotify.featran.transformers._
import com.spotify.featran._
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
class TransformerBenchmark {

  import Fixtures._

  def benchmark[A](transformer: Transformer[A, _, _], bh: Blackhole)
                  (implicit fixture: Seq[A]): Unit = {
    implicit val fb = new NoopFeatureBuilder(bh)
    val fe = FeatureSpec.of[A].required(identity)(transformer).extract(fixture)
    fe.featureValues[Unit]
  }

  @Benchmark def binarizer(bh: Blackhole): Unit = benchmark(Binarizer("t"), bh)
  @Benchmark def bucketizer(bh: Blackhole): Unit =
    benchmark(Bucketizer("t", Array(0.0, 250.0, 500.0, 750.0, 1000.0)), bh)
  @Benchmark def hashNHotEncoder(bh: Blackhole): Unit = benchmark(HashNHotEncoder("t"), bh)
  @Benchmark def hashNHotWeightedEncoder(bh: Blackhole): Unit =
    benchmark(HashNHotWeightedEncoder("t"), bh)
  @Benchmark def hashOneHotEncoder(bh: Blackhole): Unit = benchmark(HashOneHotEncoder("t"), bh)
  @Benchmark def heavyHitters(bh: Blackhole): Unit = benchmark(HeavyHitters("t", 100), bh)
  @Benchmark def identityB(bh: Blackhole): Unit = benchmark(Identity("t"), bh)
  @Benchmark def maxAbsScaler(bh: Blackhole): Unit = benchmark(MaxAbsScaler("t"), bh)
  @Benchmark def minMaxScaler(bh: Blackhole): Unit = benchmark(MinMaxScaler("t"), bh)
  @Benchmark def nGrams(bh: Blackhole): Unit = benchmark(NGrams("t"), bh)
  @Benchmark def nHotEncoder(bh: Blackhole): Unit = benchmark(NHotEncoder("t"), bh)
  @Benchmark def nHotWeightedEncoder(bh: Blackhole): Unit = benchmark(NHotWeightedEncoder("t"), bh)
  @Benchmark def normalizer(bh: Blackhole): Unit = benchmark(Normalizer("t"), bh)
  @Benchmark def oneHotEncoder(bh: Blackhole): Unit = benchmark(OneHotEncoder("t"), bh)
  @Benchmark def polynomialExpansion(bh: Blackhole): Unit = benchmark(PolynomialExpansion("t"), bh)
  @Benchmark def quantileDiscretizer(bh: Blackhole): Unit = benchmark(QuantileDiscretizer("t"), bh)
  @Benchmark def standardScaler(bh: Blackhole): Unit = benchmark(StandardScaler("t"), bh)
  @Benchmark def vectorIdentity(bh: Blackhole): Unit =
    benchmark(VectorIdentity("t")((xs: Array[Double]) => xs), bh)
  @Benchmark def vonMisesEvaluator(bh: Blackhole): Unit =
    benchmark(VonMisesEvaluator("t", 100.0, 0.001, Array(1.0, 2.0, 3.0, 4.0, 5.0)), bh)

}

private object Fixtures {
  implicit val doubles: Seq[Double] = (0 until 1000).map(_.toDouble)
  implicit val labels: Seq[String] = (0 until 1000).map(x => "l" + (x % 50))
  implicit val nLabels: Seq[Seq[String]] = (0 until 1000).map(x => (0 until (x % 50)).map("l" + _))
  implicit val nWeightedLabels: Seq[Seq[WeightedLabel]] = nLabels.map(_.map(WeightedLabel(_, 1.0)))
  implicit val vectors: Seq[Array[Double]] = (0 until 1000).map(x => Array.fill(10)(x / 1000.0))
}

private class NoopFeatureBuilder(val bh: Blackhole) extends FeatureBuilder[Unit] {
  override def init(dimension: Int): Unit = bh.consume(dimension)
  override def result: Unit = bh.consume(Unit)
  override def add(name: String, value: Double): Unit = {
    bh.consume(name)
    bh.consume(value)
  }
  override def skip(): Unit = bh.consume(Unit)
}
