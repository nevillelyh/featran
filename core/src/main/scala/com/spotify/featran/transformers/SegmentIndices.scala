package com.spotify.featran.transformers

import com.spotify.featran.{FeatureBuilder, FeatureRejection, FlatReader, FlatWriter}
import com.twitter.algebird.Aggregator

object SegmentIndices extends SettingsBuilder {

  /**
   * Create a new [[SegmentIndices]] instance.
   * @param expectedLength expected length of the input vectors, or 0 to infer from data
   */
  def apply(
             name: String,
             expectedLength: Int = 0
           ): Transformer[Array[Int], Int, Int] =
    new SegmentIndices(name, expectedLength)

  /**
   * Create a new [[SegmentIndices]] from a settings object
   * @param setting Settings object
   */
  def fromSettings(setting: Settings): Transformer[Array[Int], Int, Int] = {
    val expectedLength = setting.params("expectedLength").toInt
    SegmentIndices(setting.name, expectedLength)
  }
}

private[featran] class SegmentIndices(name: String, expectedLength: Int = 0)
  extends Transformer[Array[Int], Int, Int](name) {

  override val aggregator: Aggregator[Array[Int], Int, Int] =
    Aggregators.seqLength(expectedLength)
  override def featureDimension(c: Int): Int = c
  override def featureNames(c: Int): Seq[String] = names(c)

  override def buildFeatures(a: Option[Array[Int]], c: Int, fb: FeatureBuilder[_]): Unit = a match {
    case Some(x) if (x.length != c) =>
      fb.skip(c)
      fb.reject(this, FeatureRejection.WrongDimension(c, x.length))
    case Some(x) if (!isMonotonic(x)) =>
      fb.skip(c)
      fb.reject(this, FeatureRejection.InvalidInput("Require an increasing sequence of numbers to use SegementIndices."))
    case Some(x) =>
      val (segmentedIndices, _) = x.zipWithIndex.foldLeft((Array.empty[Int], 0)){
        case (_, (xElement, 0)) => (Array(0), xElement)
        case ((segments, previousXElement), (xElement, index)) if (xElement == previousXElement) => (segments ++ Array(segments(index - 1) + 1), xElement)
        case ((segments, _), (xElement, _))  => (segments ++ Array(0), xElement)
      }

      fb.addInts(names = names(c), values = segmentedIndices)
    case None => fb.skip(c)
  }

  override def encodeAggregator(c: Int): String = c.toString
  override def decodeAggregator(s: String): Int = s.toInt
  override def params: Map[String, String] =
    Map("expectedLength" -> expectedLength.toString)

  override def flatRead[T: FlatReader]: T => Option[Any] = FlatReader[T].readIntArray(name)

  override def flatWriter[T](implicit fw: FlatWriter[T]): Option[Array[Int]] => fw.IF = fw.writeIntArray(name)

  private def isMonotonic(arr:Array[Int]): Boolean =
    (arr, arr.drop(1)).zipped.forall (_ <= _)

}
