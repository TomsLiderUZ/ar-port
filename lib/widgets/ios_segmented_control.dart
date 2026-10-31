import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../app/theme/app_theme.dart';

class IosSegmentItem<T> {
  final T value;
  final String title;
  final Widget? icon;

  const IosSegmentItem({
    required this.value,
    required this.title,
    this.icon,
  });
}

/// iOS sliding segmented control with spring animated pill and haptic feedback.
class IosSegmentedControl<T> extends StatelessWidget {
  final List<IosSegmentItem<T>> items;
  final T selectedValue;
  final ValueChanged<T> onValueChanged;
  final double height;

  const IosSegmentedControl({
    super.key,
    required this.items,
    required this.selectedValue,
    required this.onValueChanged,
    this.height = 44,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    final containerBg = isDark
        ? const Color(0xFF1C1C1E)
        : const Color(0xFFE5E5EA);

    final selectedBg = isDark
        ? const Color(0xFF3A3A3C)
        : Colors.white;

    final selectedIndex = items.indexWhere((item) => item.value == selectedValue);

    return Container(
      height: height,
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: containerBg,
        borderRadius: BorderRadius.circular(AppTheme.controlRadius),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final itemWidth = (constraints.maxWidth) / items.length;

          return Stack(
            children: [
              // Sliding Selection Pill
              if (selectedIndex >= 0)
                AnimatedPositioned(
                  duration: const Duration(milliseconds: 220),
                  curve: Curves.easeOutCubic,
                  left: selectedIndex * itemWidth,
                  top: 0,
                  bottom: 0,
                  width: itemWidth,
                  child: Container(
                    decoration: BoxDecoration(
                      color: selectedBg,
                      borderRadius: BorderRadius.circular(AppTheme.controlRadius - 3),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: isDark ? 0.35 : 0.08),
                          blurRadius: 6,
                          offset: const Offset(0, 2),
                        ),
                      ],
                    ),
                  ),
                ),
              // Segment Labels
              Row(
                children: items.map((item) {
                  final isSelected = item.value == selectedValue;
                  return Expanded(
                    child: GestureDetector(
                      onTap: () {
                        if (!isSelected) {
                          HapticFeedback.lightImpact();
                          onValueChanged(item.value);
                        }
                      },
                      behavior: HitTestBehavior.opaque,
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 6),
                        alignment: Alignment.center,
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            if (item.icon != null) ...[
                              item.icon!,
                              const SizedBox(width: 6),
                            ],
                            Flexible(
                              child: Text(
                                item.title,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: TextStyle(
                                  fontSize: 13.5,
                                  fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                                  color: isSelected
                                      ? (isDark ? Colors.white : Colors.black)
                                      : (isDark ? AppTheme.darkSecondaryLabel : AppTheme.lightSecondaryLabel),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ],
          );
        },
      ),
    );
  }
}
