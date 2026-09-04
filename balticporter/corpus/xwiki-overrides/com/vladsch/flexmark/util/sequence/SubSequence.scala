/*
 * HANDWRITTEN OVERRIDE — Baltic Porter PLAN §7 whole-file override.
 *
 * Ported from: flexmark-java/flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/SubSequence.java
 * Original license: BSD-2-Clause (see flexmark-java upstream)
 *
 * Why hand-ported: both private constructors call super(hash) with DIFFERENT values
 * (`hashCode()`-or-0, and 0), so the funnel can't synthesize a single primary and
 * emitted `extends BasedSequenceImpl` with no super arg (BasedSequenceImpl needs an
 * Int hash). A private no-arg primary passes super(0); each aux ctor overwrites
 * `this.hash` after delegating. Also the getOption[T] ternary cast is T, not Any.
 */
package com.vladsch.flexmark.util.sequence

/**
 * A BasedSequence implementation which wraps original CharSequence to provide a BasedSequence for
 * all its subsequences, a subSequence() returns a SubSequence from the original base sequence.
 * <p>
 * NOTE: '\0' changed to '\uFFFD' use {@link com.vladsch.flexmark.util.sequence.mappers.NullEncoder#decodeNull} mapper to get original null chars. */
final class SubSequence private () extends BasedSequenceImpl(0) {
  private var charSequence: CharSequence = null

  private var baseSeq: SubSequence = null

  private var startOffset: Int = 0

  private var endOffset: Int = 0

  // NOTE: called only from baseSubSequence
  private def this(_subSequence: SubSequence, startIndex: Int, endIndex: Int) = {
    this()
    this.hash = 0
    assert((((startIndex >= 0) && (endIndex >= startIndex)) && (endIndex <= _subSequence.length())), String.format("SubSequence must have startIndex >= 0 && endIndex >= startIndex && endIndex <= %d, got startIndex:%d, endIndex: %d", _subSequence.length(), startIndex, endIndex))
    this.baseSeq = _subSequence
    this.charSequence = _subSequence.charSequence
    this.startOffset = startIndex
    this.endOffset = endIndex
  }

  private def this(_charSequence: CharSequence) = {
    this()
    this.hash = (if (_charSequence.isInstanceOf[String]) _charSequence.hashCode() else 0)
    assert((!_charSequence.isInstanceOf[BasedSequence]))
    this.baseSeq = this
    this.charSequence = _charSequence
    this.startOffset = 0
    this.endOffset = _charSequence.length()
  }

  override def getBaseSequence(): SubSequence = {
    this.baseSeq
  }

  override def getOptionFlags(): Int = {
    (if (this.charSequence.isInstanceOf[BasedOptionsHolder]) this.charSequence.asInstanceOf[BasedOptionsHolder].getOptionFlags() else 0)
  }

  override def allOptions(options: Int): Boolean = {
    (this.charSequence.isInstanceOf[BasedOptionsHolder] && this.charSequence.asInstanceOf[BasedOptionsHolder].allOptions(options))
  }

  override def anyOptions(options: Int): Boolean = {
    (this.charSequence.isInstanceOf[BasedOptionsHolder] && this.charSequence.asInstanceOf[BasedOptionsHolder].anyOptions(options))
  }

  override def getOption[T](dataKey: com.vladsch.flexmark.util.data.DataKeyBase[T]): T = {
    (if (this.charSequence.isInstanceOf[BasedOptionsHolder]) this.charSequence.asInstanceOf[BasedOptionsHolder].getOption(dataKey).asInstanceOf[T] else dataKey.get(null))
  }

  override def getOptions(): com.vladsch.flexmark.util.data.DataHolder = {
    (if (this.charSequence.isInstanceOf[BasedOptionsHolder]) this.charSequence.asInstanceOf[BasedOptionsHolder].getOptions() else null.asInstanceOf[com.vladsch.flexmark.util.data.DataHolder])
  }

  override def getBase(): CharSequence = {
    this.charSequence
  }

  def getStartOffset(): Int = {
    this.startOffset
  }

  def getEndOffset(): Int = {
    this.endOffset
  }

  override def addSegments(builder: com.vladsch.flexmark.util.sequence.builder.IBasedSegmentBuilder[? <: Any]): Unit = {
    assert(((builder.getBaseSequence() == this.baseSeq) || builder.getBaseSequence().equals(this.baseSeq)))
    builder.append(this.startOffset, this.endOffset)
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
    val c: Char = this.charSequence.charAt((index + this.startOffset))
    (if ((c == SequenceUtils.NUL)) SequenceUtils.ENC_NUL else c)
  }

  override def subSequence(startIndex: Int, endIndex: Int): SubSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, length())
    baseSubSequence((this.startOffset + startIndex), (this.startOffset + endIndex))
  }

  override def baseSubSequence(startIndex: Int, endIndex: Int): SubSequence = {
    SequenceUtils.validateStartEnd(startIndex, endIndex, this.baseSeq.length())
    (if (((startIndex == this.startOffset) && (endIndex == this.endOffset))) this else (if ((this.baseSeq != this)) this.baseSeq.baseSubSequence(startIndex, endIndex) else new SubSequence(this, startIndex, endIndex)))
  }

}

object SubSequence {
  private[sequence] def create(charSequence: CharSequence): BasedSequence = {
    if ((charSequence == null)) {
      return BasedSequence.NULL
    } else {
      if (charSequence.isInstanceOf[BasedSequence]) {
        return charSequence.asInstanceOf[BasedSequence]
      } else {
        return new SubSequence(charSequence)
      }
    }
  }

  @scala.deprecated
  def of(charSequence: CharSequence): BasedSequence = {
    BasedSequence.of(charSequence)
  }

  @scala.deprecated
  def of(charSequence: CharSequence, startIndex: Int): BasedSequence = {
    BasedSequence.of(charSequence).subSequence(startIndex, (if ((charSequence == null)) 0 else charSequence.length()))
  }

  @scala.deprecated
  def of(charSequence: CharSequence, startIndex: Int, endIndex: Int): BasedSequence = {
    BasedSequence.of(charSequence).subSequence(startIndex, endIndex)
  }

}
