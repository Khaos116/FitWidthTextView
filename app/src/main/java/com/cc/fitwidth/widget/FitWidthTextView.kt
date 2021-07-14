package com.cc.fitwidth.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.text.*
import android.text.style.*
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.cc.fitwidth.widget.emoji.EmojiManager
import com.cc.fitwidth.widget.emoji.core.Range
import com.cc.fitwidth.widget.emoji.core.RangeBean
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * @Description 主要解决中英文符号换行参差不齐的问题
 *
 * 功能说明：
 *    1.FitWidthTextView解决了中英文符号等还可以显示就换行导致文本参差不齐的bug
 *    2.FitWidthTextView解决了网络上很多自定义文本绘制拆分出现Emoji异常的bug
 *    3.FitWidthTextView支持段落缩进和段间距倍数设置(倍数基数为绘制的文本高度)
 *    4.FitWidthTextView会自动删除多个空白行(多个换行符、换行符+空格的多个循环组合)
 *    5.FitWidthTextView支持设置最大空格处理(比如文本中有连续的8个空格，则会缩减至指定的个数)
 * 注意事项：
 *    1.如果要修改文字大小和颜色，请在设置文字之前进行设置，最好最后一步设置文字，因为涉及到绘制前的准备
 *    2.由于是自定义绘制的文字，所以无法使用TextView的gravity属性
 *    3.FitWidthTextView控件的宽度不能太小，否则可能出现异常(至少要保证最大的一个Emoji能显示全)
 *    4.由于控件支持了Emoji的段行处理，所以拷贝了三方的Emoji判断部分，导致文件较多(20多个文件)
 *    5.由于采用的for循环处理文本，所以如果文本太长可能导致ANR，需要自己修改处理文本部分
 *    6.为了防止多次测量文本高度，采用了临时变量的方式防止重复测量，如果遇到测量问题，可能需要修改
 *    7.由于Emoji一直在更新，所以可能遇到Emoji数据显示不全的问题，这需要更新Emoji库
 *    8.只支持前景和背景色改变的Span
 *
 * @Author：Khaos
 * @Date：2021-07-10
 * @Time：14:59
 */
class FitWidthTextView @kotlin.jvm.JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {
  //<editor-fold defaultstate="collapsed" desc="变量">
  //分段文字(每段文字由"整行文字"+"类型:0普通1背景色2前景色3背景和前景色"+"开始+结束位置+颜色")
  private var mLineList = mutableListOf<RangeBean>()

  //用于测量字符宽度
  private var mPaint = TextPaint()

  //段间距 倍数(需要大于1且大于行间距】(文字绘制高度 * 倍数 = 绘制间距)
  var mParagraphMultiplier: Float = 1.0f

  //段行缩进空格(中文情况下的缩进，非中文会自动切换)
  var mParagraphSpace: String = "        "

  //首行缩进
  var mFirstParagraphSpace: String = ""

  //允许最大的连续空格数量
  var mMaxConsecutiveSpace: Int = 4
  //</editor-fold>

  ////<editor-fold defaultstate="collapsed" desc="重写">
  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    if (text.isNotBlank()) {
      val widthMode = MeasureSpec.getMode(widthMeasureSpec)
      val heightMode = MeasureSpec.getMode(heightMeasureSpec)
      val widthSize = MeasureSpec.getSize(widthMeasureSpec)
      val heightSize = MeasureSpec.getSize(heightMeasureSpec)
      val width: Int = when (widthMode) {
        MeasureSpec.UNSPECIFIED -> context.resources.displayMetrics.widthPixels
        else -> widthSize
      }
      val height: Int = when (heightMode) {
        MeasureSpec.EXACTLY -> heightSize
        else -> try {
          (measureContentHeight(text, width - paddingStart - paddingEnd) + 1).toInt()
        } catch (e: Exception) {
          e.printStackTrace()
          100
        }
      }
      setMeasuredDimension(width, height)
    }
  }

  override fun setText(text: CharSequence?, type: BufferType?) {
    super.setText(text, type)
    requestLayout()
  }

  @SuppressLint("DrawAllocation")
  override fun onDraw(canvas: Canvas) {
    mLineList.let { l ->
      //文字位置参考https://github.com/changer0/LineBreakTextView  貌似绘制位置是以baseline为准的
      val fontMetrics = mPaint.fontMetrics
      //文字便宜量
      val offSet = mPaint.baselineShift - fontMetrics.top
      //文字实际绘制高度
      val lineHeight = fontMetrics.bottom - fontMetrics.top
      //每行累计高度
      var drawHeight = paddingTop * 1f
      l.forEach { b ->
        val s = b.sb
        if (s.toString() != "\n") {
          //绘制背景
          for (rangeBg in b.ranges) {
            if (rangeBg.type == 1 || rangeBg.type == 3) {
              mPaint.color = rangeBg.bgColor
              val start = paddingStart * 1f + mPaint.measureText(s, 0, rangeBg.start)
              val end = start + paddingStart * 1f + mPaint.measureText(s, rangeBg.start, rangeBg.end)
              canvas.drawRect(start, drawHeight, end, drawHeight + lineHeight, mPaint)
            }
          }
          //绘制文本
          mPaint.color = currentTextColor
          canvas.drawText(s, 0, s.length, paddingStart * 1f, drawHeight + offSet, mPaint)
          //绘制前景
          for (rangeFore in b.ranges) {
            if (rangeFore.type == 2 || rangeFore.type == 3) {
              mPaint.color = rangeFore.foreColor
              val start = paddingStart * 1f + mPaint.measureText(s, 0, rangeFore.start)
              canvas.drawText(s, rangeFore.start, rangeFore.end, start, drawHeight + offSet, mPaint)
            }
          }
          drawHeight += lineHeight + lineHeight * (lineSpacingMultiplier - 1f)
        } else {
          //如果段间距不大于行间距，则不添加行间距;否则添加间距为"段间距-行间距"(因为默认底部有一个行间距)
          drawHeight += if (mParagraphMultiplier > lineSpacingMultiplier) {
            lineHeight * (mParagraphMultiplier - lineSpacingMultiplier)
          } else 0f
        }
      }
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="初始化">
  init {
    mPaint.isAntiAlias = true
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="内部工具">
  private var mLastCs: CharSequence = ""
  private var mLastAvailableWidth: Int = 0
  private var mLastHeight: Float = 0f
  private var mLastTextSize: Float = 0f

  //测量高度(Span不能被破坏，否则会找不到起始位置，所以先进行span的保留)
  private fun measureContentHeight(text: CharSequence, availableWidth: Int): Float {
    //----------------------------------防止重复走----------------------------------//
    if (text.isBlank()) return 0f
    if (mLastCs == text && mLastAvailableWidth == availableWidth && mLastTextSize == textSize) return mLastHeight
    mLastCs = text
    mLastTextSize = textSize
    mLastAvailableWidth = availableWidth
    //文字绘制大小
    mPaint.textSize = textSize
    //文字绘制的实际高度
    val lineHeight = mPaint.fontMetrics.bottom - mPaint.fontMetrics.top
    //----------------------------------没有中文缩进自动减半----------------------------------//
    var tempSpaceFirst = mFirstParagraphSpace
    if (mFirstParagraphSpace.length > 2 && !isContainChinese(text.toString())) {
      tempSpaceFirst = mFirstParagraphSpace.substring(mFirstParagraphSpace.length / 2)
    }
    var tempSpace = mParagraphSpace
    if (mParagraphSpace.length > 2 && !isContainChinese(text.toString())) {
      tempSpace = mParagraphSpace.substring(mParagraphSpace.length / 2)
    }
    //----------------------------------Span分隔(文字+颜色)----------------------------------//
    val splits = mutableListOf<RangeBean>()
    //添加首行缩进
    if (mFirstParagraphSpace.isNotEmpty()) splits.add(RangeBean(tempSpaceFirst))
    if (text is SpannedString) {
      val spans = text.getSpans(0, text.length, CharacterStyle::class.java)
      if (spans.isNullOrEmpty()) { //没有找到span
        splits.add(RangeBean(dealSpaceBreak(text, tempSpace)))
      } else {
        var index = 0
        for (span in spans) { //将普通文本和Span分隔
          val start = text.getSpanStart(span)
          val end = text.getSpanEnd(span)
          if (start > index) { //添加非span
            splits.add(RangeBean(dealSpaceBreak(text.subSequence(index, start), tempSpace)))
          }
          //获取当前span文本
          val txt = text.subSequence(start, end)
          //判断span
          if (span is BackgroundColorSpan && span is ForegroundColorSpan) {
            splits.add(RangeBean(txt, mutableListOf(Range(type = 3, bgColor = span.backgroundColor, foreColor = span.foregroundColor))))
          } else if (span is BackgroundColorSpan) {
            splits.add(RangeBean(txt, mutableListOf(Range(type = 1, bgColor = span.backgroundColor))))
          } else if (span is ForegroundColorSpan) {
            splits.add(RangeBean(txt, mutableListOf(Range(type = 2, foreColor = span.foregroundColor))))
          } else {
            splits.add(RangeBean(dealSpaceBreak(txt, tempSpace)))
          }
          index = end
        }
        if (index < text.length) splits.add(RangeBean(dealSpaceBreak(text.subSequence(index, text.length), tempSpace)))
      }
    } else {
      splits.add(RangeBean(dealSpaceBreak(text, tempSpace)))
    }
    //----------------------------------换行分隔----------------------------------//
    //处理后的分行文本(一个为一行)
    val resultList = mutableListOf<RangeBean>()
    //拼接裁切后剩余不足一行的部分
    val remainSb = SpannableStringBuilder()
    //非普通文本的范围
    var remainRange = mutableListOf<Range>()
    //循环拼接
    for (index in 0 until splits.size) {
      val split = splits[index]
      //如果不是普通文本，则拼接后重新计算位置
      split.ranges.firstOrNull { f -> f.type != 0 }?.let { r ->
        r.start = remainSb.length
        r.end = r.start + split.sb.length
        remainRange.add(r)
      }
      //剩余的加上现在的再测量
      remainSb.append(split.sb)
      //测量拼接后的长度
      val w = mPaint.measureText(remainSb, 0, remainSb.length)
      if (w < availableWidth && !remainSb.toString().contains("\n")) { //填不满宽度并且没有换行符，累计临时值
        if (index == splits.size - 1) { //如果是最后一个了
          resultList.add(RangeBean(remainSb.subSequence(0, remainSb.length), remainRange))
          remainSb.delete(0, remainSb.length)
          remainRange = mutableListOf()
          break
        } else continue
      } else { //大于宽度，需要裁切
        //只要有换行符或者能填满宽度，就继续处理
        while (remainSb.toString().contains("\n") || mPaint.measureText(remainSb, 0, remainSb.length) >= availableWidth) {
          //找出所有emoji表情
          val emojis = EmojiManager.getInstance().findAllEmojis(remainSb)
          for (i in remainSb.indices) { //遍历判断长度
            //遇到换行符的处理
            if (remainSb.subSequence(i, i + 1).toString() == "\n") {
              //裁切
              val txt = remainSb.subSequence(0, i)
              remainSb.delete(0, i + 1)
              //被裁切的span
              val splitRange = remainRange.filter { r -> r.end < (txt.length + 1) }.toMutableList()
              //span被分隔的处理
              val cutSpan = remainRange.firstOrNull { f -> (txt.length + 1) in f.start..f.end }
              //新余下的span
              remainRange = remainRange.filter { r -> r.start > (txt.length + 1) }.map { r ->
                r.start -= (txt.length + 1)
                r.end -= (txt.length + 1)
                r
              }.toMutableList()
              //分隔成2个span
              if (cutSpan != null) {
                val span1 = cutSpan.copy(end = txt.length)
                val span2 = cutSpan.copy(start = 0, end = cutSpan.end - (txt.length + 1))
                splitRange.add(span1) //添加到最后
                remainRange.add(0, span2) //添加到最前面
              }
              resultList.add(RangeBean(txt, splitRange))
              //确保最后一个不是换行符
              if (!(index == splits.size - 1 && i == remainSb.length - 1)) {
                resultList.add(RangeBean("\n"))
              }
              break
            }
            val w2 = mPaint.measureText(remainSb, 0, i + 1)
            if (w2 == 0f) continue //可能测量到空制符
            if (w2 > availableWidth) { //需要换行了
              //优先判断是否会分隔Emoji
              val emojiRange = emojis.firstOrNull { e -> i in e.start until e.end }
              //文本裁切结束位置
              var end = i
              if (emojiRange != null) { //如果Emoji被拆分了
                //测试是否可用完整显示，如果不行就拆到下一行(有可能不完整的Emoji放不下，完整的反而可用放下)
                val w3 = mPaint.measureText(remainSb, 0, emojiRange.end)
                end = if (w3 > maxWidth) { //换到下一行
                  emojiRange.start
                } else { //本行结束
                  emojiRange.end
                }
              }
              //裁切
              val txt = remainSb.subSequence(0, end)
              remainSb.delete(0, end)
              //被裁切的span
              val splitRange = remainRange.filter { r -> r.end < txt.length }.toMutableList()
              //span被分隔的处理
              val cutSpan = remainRange.firstOrNull { f -> txt.length in f.start..f.end }
              //新余下的span
              remainRange = remainRange.filter { r -> r.start > txt.length }.map { r ->
                r.start -= txt.length
                r.end -= txt.length
                r
              }.toMutableList()
              //分隔成2个span
              if (cutSpan != null) {
                val span1 = cutSpan.copy(end = txt.length)
                val span2 = cutSpan.copy(start = 0, end = cutSpan.end - txt.length)
                splitRange.add(span1) //添加到最后
                remainRange.add(0, span2) //添加到最前面
              }
              resultList.add(RangeBean(txt, splitRange))
              break
            }
          }
        }
        //如果是最后一个了
        if (index == splits.size - 1 && remainSb.isNotEmpty()) {
          resultList.add(RangeBean(remainSb.subSequence(0, remainSb.length), remainRange))
          remainSb.delete(0, remainSb.length)
          remainRange = mutableListOf()
          break
        }
      }
    }
    val countBreak = resultList.count { r -> r.sb.toString() == "\n" }
    var totalHeight = countBreak * (lineHeight * (mParagraphMultiplier - 1f)) + (resultList.size - countBreak) * (lineHeight * lineSpacingMultiplier)
    if (resultList.isNotEmpty()) totalHeight -= lineHeight * (lineSpacingMultiplier - 1f) //最后一行不要行间距
    mLastHeight = totalHeight + paddingTop + paddingBottom
    mLineList.clear()
    mLineList.addAll(resultList)
    return mLastHeight
  }

  //普通文本处理多个空白行和空格保留问题
  private fun dealSpaceBreak(cs: CharSequence, paragraphSpace: String): CharSequence {
    val sb = StringBuilder() //处理后的文本
    val sbSpace = StringBuilder() //临时存放空格，可以设置最大连续数量
    for (c in cs) { //遍历每个字符
      if (c.toString() == "\r") continue //不处理
      if (c.toString() == " " || c.toString() == "\t") { //空格处理
        val endSpaceBreak = sb.toString().endsWith("\n") || sb.toString().endsWith(" ") || sb.toString().endsWith("\t")
        if (sb.isNotEmpty() && !endSpaceBreak && sbSpace.length < mMaxConsecutiveSpace) sbSpace.append(" ") //文本最开始不加空格，超过最大数量不加空格
      } else if (c.toString() == "\n") {
        if (sbSpace.isNotEmpty()) sbSpace.delete(0, sbSpace.length) //遇到换行则把想要添加的空格移除，直接添加换行符
        val endBreak = sb.toString().replace(" ", "").replace("\t", "").endsWith("\n")
        if (sb.isNotEmpty() && !endBreak) { //文本不为空，且不以换行结尾才添加换行
          sb.append("\n")
          if (paragraphSpace.isNotEmpty()) {
            sb.append(paragraphSpace) //添加段落缩进
            sbSpace.delete(0, sbSpace.length) //删除需要添加的空格
          }
        }
      } else {
        if (sbSpace.isNotEmpty()) { //文本中间需要的空格添加
          sb.append(sbSpace.toString())
          sbSpace.delete(0, sbSpace.length) //添加完成后删除临时空格
        }
        sb.append(c) //正常添加字符
      }
    }
    return sb
  }

  //判断是否包含中文
  private fun isContainChinese(str: String): Boolean {
    val p: Pattern = Pattern.compile("[\\u4e00-\\u9fa5]")
    val m: Matcher = p.matcher(str)
    return m.find()
  }
  //</editor-fold>
}