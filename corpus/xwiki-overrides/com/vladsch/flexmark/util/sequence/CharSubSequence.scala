/*
 * HANDWRITTEN OVERRIDE — Baltic Porter PLAN §7 whole-file override.
 *
 * Ported from: flexmark-java/flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/CharSubSequence.java
 * Original license: BSD-2-Clause (see flexmark-java upstream)
 *
 * Why hand-ported: same as SubSequence — the private constructors call super(hash)
 * with differing values, so the funnel emitted `extends BasedSequenceImpl` with no
 * super arg. A private no-arg primary passes super(0); each aux ctor overwrites
 * `this.hash` after delegating.
 */
package com.vladsch.flexmark.util.sequence

/**
 * A CharSequence that references original char[]
 * a subSequence() returns a sub-sequence from the original base sequence
 * <p>
 * NOTE: '\0' changed to '\uFFFD' use {@link com.vladsch.flexmark.util.sequence.mappers.NullEncoder#decodeNull} mapper to get original null chars.
 */
final class CharSubSequence private () extends BasedSequenceImpl(0) {
  private var baseChars: Array[Char] = null

  private var base: CharSubSequence = null

  private var startOffset: Int = 0

  private var endOffset: Int = 0

  private def this(chars: Array[Char], _hash: Int) = {
    this()
    var hash: Int = _hash
    this.hash = hash
    val iMax: Int = chars.length
    this.base = this
    this.baseChars = chars
    this.startOffset = 0
    this.endOffset = this.baseChars.length
  }

  private def this(baseSeq: CharSubSequence, startIndex: Int, endIndex: Int) = {
    this()
    this.hash = 0
    assert((((startIndex >= 0) && (endIndex >= startIndex)) && (endIndex <= baseSeq.baseChars.length)), String.format("CharSubSequence must have (startIndex > 0 || endIndex < %d) && endIndex >= startIndex, got startIndex:%d, endIndex: %d", baseSeq.baseChars.length, startIndex, endIndex))
    assert(((startIndex > 0) || (endIndex < baseSeq.baseChars.length)), String.format("CharSubSequence must be proper subsequences [1, %d) got startIndex:%d, endIndex: %d", Math.max(0, (baseSeq.baseChars.length - 1)), startIndex, endIndex))
    this.base = baseSeq
    this.baseChars = baseSeq.baseChars
    this.startOffset = (this.base.startOffset + startIndex)
    this.endOffset = (this.base.startOffset + endIndex)
  }

  override def getOptionFlags(): Int = {
    0
  }

  override def allOptions(options: Int): Boolean = {
    false
  }

  override def anyOptions(options: Int): Boolean = {
    false
  }

  override def getOption[T](dataKey: com.vladsch.flexmark.util.data.DataKeyBase[T]): T = {
    dataKey.get(null).asInstanceOf[T]
  }

  override def getOptions(): com.vladsch.flexmark.util.data.DataHolder = {
    null
  }

  override def getBaseSequence(): CharSubSequence = {
    this.base
  }

  override def getBase(): Array[Char] = {
    this.baseChars
  }

  def getStartOffset(): Int = {
    this.startOffset
  }

  def getEndOffset(): Int = {
    this.endOffset
  }

  override def length(): Int = {
    (this.endOffset - this.startOffset)
  }

  override def getSourceRange(): Range = {
    Range.of(this.startOffset, this.endOffset)
  }

  override def getIndexOffset(index: Int): Int = {
    SequenceUtils.validateIndexInclusiveEnd(index, length())
    (this.startOffset + index)
  }

  override def charAt(index: Int): Char = {
    SequenceUtils.validateIndex(index, length())
    val c: Char = this.baseChars((index + this.startOffset))
    (if ((c == SequenceUtils.NUL)) SequenceUtils.ENC_NUL else c)
  }

  override def subSequence(startIndex: Int, endIndex: Int): CharSubSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, length())
    this.base.baseSubSequence((this.startOffset + startIndex), (this.startOffset + endIndex))
  }

  override def baseSubSequence(startIndex: Int, endIndex: Int): CharSubSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, this.baseChars.length)
    (if (((startIndex == this.startOffset) && (endIndex == this.endOffset))) this else (if ((this.base != this)) this.base.baseSubSequence(startIndex, endIndex) else new CharSubSequence(this.base, startIndex, endIndex)))
  }

}

object CharSubSequence {
  def of(charSequence: CharSequence): CharSubSequence = {
    CharSubSequence.of(charSequence, 0, charSequence.length())
  }

  def of(charSequence: CharSequence, startIndex: Int): CharSubSequence = {
    assert((startIndex <= charSequence.length()))
    CharSubSequence.of(charSequence, startIndex, charSequence.length())
  }

  /**
   * @param chars      char array
   * @param startIndex start index in array
   * @param endIndex   end index in array
   * @return CharSubSequence based sequence of array
   * @deprecated NOTE: use BasedSequence.of() for creating based sequences
   */
  @scala.deprecated
  def of(chars: Array[Char], startIndex: Int, endIndex: Int): CharSubSequence = {
    assert((((startIndex >= 0) && (startIndex <= endIndex)) && (endIndex <= chars.length)))
    val useChars: Array[Char] = new Array[Char](chars.length)
    System.arraycopy(chars, 0, useChars, 0, chars.length)
    (if (((startIndex == 0) && (endIndex == chars.length))) new CharSubSequence(useChars, 0) else new CharSubSequence(useChars, 0).subSequence(startIndex, endIndex))
  }

  /**
   * @param charSequence char sequence
   * @param startIndex   start index in sequence
   * @param endIndex     end index in sequence
   * @return char based sequence
   */
  private def of(charSequence: CharSequence, startIndex: Int, endIndex: Int): CharSubSequence = {
    assert((((startIndex >= 0) && (startIndex <= endIndex)) && (endIndex <= charSequence.length())))
    var charSubSequence: CharSubSequence = null
    if (charSequence.isInstanceOf[CharSubSequence]) {
      charSubSequence = charSequence.asInstanceOf[CharSubSequence]
    } else {
      if (charSequence.isInstanceOf[String]) {
        charSubSequence = new CharSubSequence(charSequence.asInstanceOf[String].toCharArray(), charSequence.asInstanceOf[String].hashCode())
      } else {
        if (charSequence.isInstanceOf[java.lang.StringBuilder]) {
          val chars: Array[Char] = new Array[Char](charSequence.length())
          charSequence.asInstanceOf[java.lang.StringBuilder].getChars(0, charSequence.length(), chars, 0)
          charSubSequence = new CharSubSequence(chars, 0)
        } else {
          charSubSequence = new CharSubSequence(charSequence.toString().toCharArray(), 0)
        }
      }
    }
    if (((startIndex == 0) && (endIndex == charSequence.length()))) {
      return charSubSequence
    } else {
      return charSubSequence.subSequence(startIndex, endIndex)
    }
  }

}
