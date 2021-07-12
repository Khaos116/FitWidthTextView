package com.cc.fitwidth.widget

import android.content.Context
import android.graphics.Canvas
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.cc.fitwidth.widget.emoji.EmojiManager
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
  //分段文字
  private var mLineList = mutableListOf<CharSequence>()

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

  //<editor-fold defaultstate="collapsed" desc="重写">
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
        else -> (measureContentHeight(text, width - paddingStart - paddingEnd) + 1).toInt()
      }
      setMeasuredDimension(width, height)
    }
  }

  override fun setText(text: CharSequence?, type: BufferType?) {
    super.setText(text, type)
    requestLayout()
  }

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
      l.forEach { s ->
        if (s.toString() != "\n") {
          //mPaint.color = Color.MAGENTA
          //canvas.drawLine(paddingStart * 1f, drawHeight, (width - paddingEnd) * 1f, drawHeight, mPaint)
          mPaint.color = currentTextColor
          if (s.length > 1 && !s.startsWith("  ")) {
            canvas.drawText(s, 0, s.length, paddingStart * 1f, drawHeight + offSet, mPaint)
          } else {
            canvas.drawText(s, 0, s.length, paddingStart * 1f, drawHeight + offSet, mPaint)
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

  //测量高度
  private fun measureContentHeight(text: CharSequence, availableWidth: Int): Float {
    if (mLastCs == text && mLastAvailableWidth == availableWidth && mLastTextSize == textSize) return mLastHeight
    mLastCs = text
    mLastTextSize = textSize
    mLastAvailableWidth = availableWidth
    //没有中文缩进自动减半
    var tempSpaceFirst = mFirstParagraphSpace
    if (mFirstParagraphSpace.length > 2 && !isContainChinese(text.toString())) {
      tempSpaceFirst = mFirstParagraphSpace.substring(mFirstParagraphSpace.length / 2)
    }
    var tempSpace = mParagraphSpace
    if (mParagraphSpace.length > 2 && !isContainChinese(text.toString())) {
      tempSpace = mParagraphSpace.substring(mParagraphSpace.length / 2)
    }
    //处理换行和缩进
    val content = SpannableStringBuilder()
    //防止空格太多
    val sbSpace = SpannableStringBuilder()
    for (i in text.indices) {
      val char = text.subSequence(i, i + 1)
      if (char.toString() == "\n") { //处理换行
        if (content.isBlank()) continue //第一个就是换行，则不处理
        sbSpace.delete(0, sbSpace.length) //遇到换行全部清除(删除换行前面的空格)
        val temp = content.toString().replace(" ", "") //解决换行+空格+换行+空格的问题
        if (temp.isNotEmpty() && temp.substring(temp.length - 1, temp.length) == "\n") continue //不能连续换行
        content.append("\n") //添加换行
        if (tempSpace.isNotEmpty()) content.append(tempSpace) //添加缩进
      } else if (char.toString() == " " || char.toString() == "\r" || char.toString() == "\t") { //防止连续空格太多
        if (content.isBlank()) continue //第一个就是空格，换行，跳格等便不处理
        if ((mParagraphSpace.isEmpty() || !content.toString().endsWith(mParagraphSpace)) && sbSpace.length < mMaxConsecutiveSpace) sbSpace.append(" ")
      } else { //正常添加
        if (sbSpace.isNotEmpty()) {
          content.append(sbSpace)
          sbSpace.delete(0, sbSpace.length) //添加空格后清除
        }
        if (tempSpaceFirst.isNotEmpty() && content.isBlank()) content.append(tempSpaceFirst) //首行缩进
        content.append(char)
      }
    }
    //找出所有emoji表情
    val emojis = EmojiManager.getInstance().findAllEmojis(content)
    //临时列表
    val temList = mutableListOf<CharSequence>()
    //最大可用宽度，需要减去paddingStart和paddingEnd
    val maxWidth = if (availableWidth <= 0) context.resources.displayMetrics.widthPixels - paddingStart - paddingEnd else availableWidth
    //每行拆分相对于原始数据的开始位置
    var start = 0
    //总高度
    var totalHeight = 0f
    //测量前确定文字大小
    mPaint.textSize = textSize
    //文字绘制的实际高度
    val lineHeight = mPaint.fontMetrics.bottom - mPaint.fontMetrics.top
    //有可能直接跳到后面去
    var offEnd = 0
    //循环测量长度，并拆分
    for (i in content.indices) {
      if (i < offEnd) continue //如果直接跳到后面，则从后面继续循环
      val char = content.subSequence(i, i + 1)
      if (char.toString() == "\n") { //遇到换行符，直接添加之前的和换行
        if (i > start) {
          temList.add(dealBreakLine(content.subSequence(start, i)))
          totalHeight += lineHeight + lineHeight * (lineSpacingMultiplier - 1f) //文字高度+行间距
        }
        temList.add("\n")
        //如果段间距不大于行间距，则不添加行间距;否则添加间距为"段间距-行间距"(因为默认底部有一个行间距)
        totalHeight += if (mParagraphMultiplier > lineSpacingMultiplier) {
          lineHeight * (mParagraphMultiplier - lineSpacingMultiplier)
        } else 0f
        start = i
      } else { //正常字符，测量宽度后计算是否换行
        val w = mPaint.measureText(content, start, i + 1)
        when {
          w > maxWidth -> { //换行
            //优先判断是否会分隔Emoji
            val emojiRange = emojis.firstOrNull { e -> i in e.start until e.end }
            if (emojiRange != null) { //如果Emoji被拆分了
              //测试是否可用完整显示，如果不行就拆到下一行(有可能不完整的Emoji放不下，完整的反而可用放下)
              val w2 = mPaint.measureText(content, start, emojiRange.end)
              start = if (w2 > maxWidth) { //换到下一行
                temList.add(dealBreakLine(content.subSequence(start, emojiRange.start)))
                emojiRange.start
              } else { //将最后一个Emoji放到上一行
                temList.add(dealBreakLine(content.subSequence(start, emojiRange.end)))
                offEnd = emojiRange.end
                emojiRange.end
              }
              totalHeight += lineHeight + lineHeight * (lineSpacingMultiplier - 1f)  //文字高度+行间距

            } else {
              temList.add(dealBreakLine(content.subSequence(start, i)))
              totalHeight += lineHeight + lineHeight * (lineSpacingMultiplier - 1f)  //文字高度+行间距
              start = i
            }
          }
          else -> { //不换行
            if (i == content.indices.last) { //最后一行
              temList.add(dealBreakLine(content.subSequence(start, content.length)))
              totalHeight += lineHeight //最后一行不添加行间距
            }
          }
        }
      }
    }
    mLineList.clear()
    mLineList.addAll(temList)
    mLastHeight = totalHeight + paddingTop + paddingBottom
    return mLastHeight
  }

  //换行处理
  private fun dealBreakLine(cs: CharSequence): SpannableStringBuilder {
    val sb = SpannableStringBuilder()
    for (index in cs.indices) {
      val c = cs.subSequence(index, index + 1)
      if (c.toString() != "\r" && c.toString() != "\n") { //换行不添加
        sb.append(c)
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