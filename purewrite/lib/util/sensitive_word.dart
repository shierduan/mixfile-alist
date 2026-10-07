// 敏感词检测工具
// 对应 Android 端 SensitiveWordManager

/// 敏感词命中结果
class SensitiveResult {
  final String word;
  final int start;
  final int end;

  SensitiveResult({
    required this.word,
    required this.start,
    required this.end,
  });
}

class SensitiveWordManager {
  /// 内置敏感词表（可扩展）
  static final Set<String> _sensitiveWords = {
    '色情', '赌博', '毒品', '暴力', '恐怖',
    '反动', '分裂', '颠覆', '邪教', '迷信',
    // 可根据需要扩展
  };

  /// 检测文本中的敏感词，返回所有命中位置
  static List<SensitiveResult> check(String text) {
    final results = <SensitiveResult>[];
    if (text.isEmpty) return results;

    for (final word in _sensitiveWords) {
      var start = 0;
      while (true) {
        final index = text.indexOf(word, start);
        if (index == -1) break;
        results.add(SensitiveResult(
          word: word,
          start: index,
          end: index + word.length,
        ));
        start = index + word.length;
      }
    }

    results.sort((a, b) => a.start.compareTo(b.start));
    return results;
  }

  /// 用 * 替换敏感词
  static String mask(String text) {
    var result = text;
    for (final word in _sensitiveWords) {
      result = result.replaceAll(word, '*' * word.length);
    }
    return result;
  }

  /// 添加自定义敏感词
  static void addWords(List<String> words) {
    _sensitiveWords.addAll(words);
  }

  /// 清空敏感词表
  static void clear() {
    _sensitiveWords.clear();
  }
}
