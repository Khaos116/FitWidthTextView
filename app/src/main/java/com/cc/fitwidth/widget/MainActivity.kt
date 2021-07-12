package com.cc.fitwidth.widget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cc.fitwidth.R
import kotlinx.android.synthetic.main.activity_main.mainTv1
import kotlinx.android.synthetic.main.activity_main.mainTv2

/**
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
 */
class MainActivity : AppCompatActivity() {
  //纯英文
  private val text1 =
    "This is a TextView that solves the uneven line breaks of mixed text such as Chinese, English, and characters.\nAt the same time, it solves the problem that other custom line breaks on the Internet cause Emoji expressions to be separated and displayed abnormally.\nThere may be some compatibility issues, so let's improve it together."

  //全中文
  private val text2 = "这是一个解决中文、英文、字符等混合文字换行参差不齐的文字控件。\n同时解决网上其他自定义换行导致Emoji表情被分隔显示异常的问题。\n可能会存在一些兼容性问题，大家一起完善吧。"

  //Emoj表情
  private val text3 =
    "\uD83D\uDC36\uD83D\uDC30\uD83E\uDD8A\uD83D\uDC3C\uD83D\uDC2F\uD83D\uDC35\uD83D\uDC37\uD83D\uDC2E\uD83E\uDD81\uD83D\uDC28\uD83D\uDC14\uD83C\uDF3F\uD83C\uDF40\uD83C\uDF38\uD83C\uDF39\uD83C\uDF34\uD83C\uDF32\uD83C\uDDE8\uD83C\uDDF3\uD83C\uDF53\uD83C\uDF4E\uD83C\uDF49\uD83C\uDF4F\uD83C\uDF51\uD83C\uDF89\uD83D\uDEB2\uD83C\uDFC5\uD83D\uDDFD\uD83D\uDC6B\uD83C\uDF1D\uD83D\uDCAF\uD83D\uDC58\uD83D\uDC57"

  //组合
  private val text4 =
    "\uD83D\uDC36\uD83D\uDC30\uD83E\uDD8A\uD83D\uDC3C\uD83D\uDC2F\uD83D\uDC35\uD83D\uDC37\uD83D\uDC2E\uD83E\uDD81\uD83D\uDC28\uD83D\uDC14\uD83C\uDF3F\uD83C\uDF40\uD83C\uDDE8\uD83C\uDDF3 This is english split\uD83C\uDF38\uD83C\uDF39\uD83C\uDF34\uD83C\uDF32\uD83C\uDF53\uD83C\uDF4E\uD83C\uDF49\uD83C\uDF4F\uD83C\uDF51\uD83C\uDF89\uD83D\uDEB2 中文并不会被拆分 \uD83C\uDFC5\uD83D\uDDFD\uD83D\uDC6B\uD83C\uDF1D\uD83D\uDCAF\uD83D\uDC58\uD83D\uDC57 轻浮的女子 Flibbertigibbet \uD83E\uDD22\uD83E\uDD9A\uD83D\uDC71\uD83C\uDFFB\u200D♀️\uD83D\uDD1E\uD83D\uDE45\uD83C\uDFFB\u200D♀️\uD83D\uDC69\uD83C\uDFFB\u200D\uD83D\uDCBB\uD83C\uDFE9\uD83D\uDC69\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC67㍿⚅㎇㍰▓\uD83D\uDCBB\uD83C\uDE32\uD83D\uDEB4\uD83C\uDFFB\u200D♂️\uD83C\uDDEC\uD83C\uDDE7✈️"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    val index = System.currentTimeMillis() % 4
    //默认带有段落缩进，这里为了对比，去掉缩进
    mainTv1.mParagraphSpace = ""
    //设置段间距倍数(倍数为文字绘制高度的倍数)
    mainTv1.mParagraphMultiplier = 1.5f
    //随机设置文字
    val text = when (index) {
      1L -> text1
      2L -> text2
      3L -> text3
      else -> text4
    }
    //两种显示对比
    mainTv1.text = text
    mainTv2.text = text
  }
}