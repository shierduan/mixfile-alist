/// 字数统计工具
/// 规则（与 Android 端 WordCounter 对齐）：
/// - 中文字符：每个汉字计 1 字
/// - 英文单词：按空格/标点分隔，连续字母算 1 词
/// - 数字：按连续数字算 1 词
/// - 标点、空格不计入
class WordCounter {
  static int count(String text) {
    if (text.isEmpty) return 0;

    var count = 0;
    var inWord = false;

    for (final rune in text.runes) {
      // 中文字符（CJK 统一表意文字基本区 + 扩展 A）
      if (rune >= 0x4E00 && rune <= 0x9FFF ||
          rune >= 0x3400 && rune <= 0x4DBF) {
        count++;
        inWord = false;
        continue;
      }

      // 英文字母或数字：连续算一个词
      final isAlphanumeric =
          (rune >= 0x61 && rune <= 0x7A) || // a-z
          (rune >= 0x41 && rune <= 0x5A) || // A-Z
          (rune >= 0x30 && rune <= 0x39); // 0-9

      if (isAlphanumeric) {
        if (!inWord) {
          count++;
          inWord = true;
        }
      } else {
        inWord = false;
      }
    }

    return count;
  }
}
