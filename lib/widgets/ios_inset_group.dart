import 'package:flutter/material.dart';
import '../app/theme/app_theme.dart';

/// Inset grouped section adhering to iOS guidelines with header, footer, and automatic separators.
class IosInsetGroup extends StatelessWidget {
  final String? header;
  final String? footer;
  final List<Widget> children;
  final EdgeInsetsGeometry margin;

  const IosInsetGroup({
    super.key,
    this.header,
    this.footer,
    required this.children,
    this.margin = const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    return Padding(
      padding: margin,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (header != null) ...[
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 6),
              child: Text(
                header!.toUpperCase(),
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  letterSpacing: -0.1,
                  color: isDark ? AppTheme.darkSecondaryLabel : AppTheme.lightSecondaryLabel,
                ),
              ),
            ),
          ],
          Container(
            decoration: BoxDecoration(
              color: theme.colorScheme.surface,
              borderRadius: BorderRadius.circular(AppTheme.cardRadius),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: isDark ? 0.25 : 0.04),
                  blurRadius: 10,
                  offset: const Offset(0, 3),
                ),
              ],
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(AppTheme.cardRadius),
              child: Column(
                children: [
                  for (int i = 0; i < children.length; i++) ...[
                    children[i],
                    if (i < children.length - 1)
                      Divider(
                        height: 0.5,
                        thickness: 0.5,
                        indent: 56,
                        color: theme.dividerColor,
                      ),
                  ],
                ],
              ),
            ),
          ),
          if (footer != null) ...[
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 6, 16, 0),
              child: Text(
                footer!,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w400,
                  letterSpacing: -0.1,
                  color: isDark ? AppTheme.darkSecondaryLabel : AppTheme.lightSecondaryLabel,
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

