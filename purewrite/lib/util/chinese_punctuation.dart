/// 中文标点转换器
/// 将英文标点智能转换为中文标点
/// 对应 Android 端 ChinesePunctuationConverter
class ChinesePunctuationConverter {
  static const Map<String, String> _punctuationMap = {
    ',': '，',
    '.': '。',
    '?': '？',
    '!': '！',
    ':': '：',
    ';': '；',
    '(': '（',
    ')': '）',
    '[': '【',
    ']': '】',
    '{': '｛',
    '}': '｝',
    '<': '《',
    '>': '》',
    '~': '～',
    '-': '—',
    '\\': '、',
  };

  /// 将文本中的英文标点转换为中文标点
  /// 智能判断：只在中文字符附近转换，避免破坏英文/代码
  static String convert(String text) {
    if (text.isEmpty) return text;

    final runes = text.runes.toList();
    final buffer = StringBuffer();

    for (var i = 0; i < runes.length; i++) {
      final current = runes[i];
      final currentChar = String.fromCharCode(current);

      final mapped = _punctuationMap[currentChar];
      if (mapped == null) {
        buffer.write(currentChar);
        continue;
      }

      // 判断是否在中文上下文（前一个或后一个字符是中文）
      final prevIsChinese = i > 0 && _isChinese(runes[i - 1]);
      final nextIsChinese =
          i < runes.length - 1 && _isChinese(runes[i + 1]);

      if (prevIsChinese || nextIsChinese) {
        buffer.write(mapped);
      } else {
        buffer.write(currentChar);
      }
    }

    return buffer.toString();
  }

  static bool _isChinese(int rune) {
    return (rune >= 0x4E00 && rune <= 0x9FFF) ||
        (rune >= 0x3400 && rune <= 0x4DBF) ||
        (rune >= 0x3000 && rune <= 0x303F) || // CJK 符号
        (rune >= 0xFF00 && rune <= 0xFFEF); // 全角字符
  }
}
