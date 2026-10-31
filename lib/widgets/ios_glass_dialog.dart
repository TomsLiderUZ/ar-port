import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../app/theme/app_theme.dart';

class IosDialogAction {
  final String title;
  final VoidCallback onPressed;
  final bool isDestructive;
  final bool isDefault;

  const IosDialogAction({
    required this.title,
    required this.onPressed,
    this.isDestructive = false,
    this.isDefault = false,
  });
}

/// Frosted liquid glass modal dialog with BackdropFilter and iOS styling.
class IosGlassDialog extends StatelessWidget {
  final String title;
  final String? message;
  final Widget? content;
  final List<IosDialogAction> actions;

  const IosGlassDialog({
    super.key,
    required this.title,
    this.message,
    this.content,
    required this.actions,
  });

  static Future<T?> show<T>({
    required BuildContext context,
    required String title,
    String? message,
    Widget? content,
    required List<IosDialogAction> actions,
    bool barrierDismissible = true,
  }) {
    HapticFeedback.mediumImpact();
    return showGeneralDialog<T>(
      context: context,
      barrierDismissible: barrierDismissible,
      barrierLabel: 'Dismiss',
      barrierColor: Colors.black.withValues(alpha: 0.45),
      transitionDuration: const Duration(milliseconds: 260),
      pageBuilder: (context, anim1, anim2) => IosGlassDialog(
        title: title,
        message: message,
        content: content,
        actions: actions,
      ),
      transitionBuilder: (context, anim1, anim2, child) {
        final curvedValue = Curves.easeOutBack.transform(anim1.value) - 1.0;
        return Transform.scale(
          scale: 1.0 + curvedValue * 0.1,
          child: Opacity(
            opacity: anim1.value.clamp(0.0, 1.0),
            child: child,
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    final glassTint = isDark
        ? const Color(0xCC252528)
        : const Color(0xE6FFFFFF);

    final borderColor = isDark
        ? Colors.white.withValues(alpha: 0.15)
        : Colors.white.withValues(alpha: 0.6);

    return Material(
      type: MaterialType.transparency,
      child: Center(
        child: Container(
          width: 310,
          margin: const EdgeInsets.symmetric(horizontal: 24),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppTheme.dialogRadius),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withValues(alpha: isDark ? 0.6 : 0.2),
                blurRadius: 32,
                offset: const Offset(0, 16),
              ),
            ],
          ),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(AppTheme.dialogRadius),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 28, sigmaY: 28),
              child: Container(
                decoration: BoxDecoration(
                  color: glassTint,
                  borderRadius: BorderRadius.circular(AppTheme.dialogRadius),
                  border: Border.all(color: borderColor, width: 1.0),
                ),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(20, 24, 20, 16),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            title,
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w700,
                              letterSpacing: -0.4,
                              color: isDark ? Colors.white : Colors.black,
                            ),
                          ),
                          if (message != null) ...[
                            const SizedBox(height: 8),
                            Text(
                              message!,
                              textAlign: TextAlign.center,
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w400,
                                letterSpacing: -0.2,
                                color: isDark ? AppTheme.darkSecondaryLabel : AppTheme.lightSecondaryLabel,
                                height: 1.35,
                              ),
                            ),
                          ],
                          if (content != null) ...[
                            const SizedBox(height: 12),
                            content!,
                          ],
                        ],
                      ),
                    ),
                    Divider(height: 0.5, thickness: 0.5, color: theme.dividerColor),
                    _buildActions(context, isDark),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildActions(BuildContext context, bool isDark) {
    final theme = Theme.of(context);

    if (actions.length == 2) {
      return IntrinsicHeight(
        child: Row(
          children: [
            Expanded(child: _buildActionButton(context, actions[0], isDark)),
            VerticalDivider(width: 0.5, thickness: 0.5, color: theme.dividerColor),
            Expanded(child: _buildActionButton(context, actions[1], isDark)),
          ],
        ),
      );
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (int i = 0; i < actions.length; i++) ...[
          _buildActionButton(context, actions[i], isDark),
          if (i < actions.length - 1)
            Divider(height: 0.5, thickness: 0.5, color: theme.dividerColor),
        ],
      ],
    );
  }

  Widget _buildActionButton(BuildContext context, IosDialogAction action, bool isDark) {
    final theme = Theme.of(context);
    Color textColor;
    if (action.isDestructive) {
      textColor = const Color(0xFFFF3B30); // iOS Red
    } else if (action.isDefault) {
      textColor = theme.colorScheme.primary;
    } else {
      textColor = isDark ? Colors.white : Colors.black;
    }

    return GestureDetector(
      onTap: () {
        HapticFeedback.lightImpact();
        action.onPressed();
      },
      behavior: HitTestBehavior.opaque,
      child: Container(
        height: 48,
        alignment: Alignment.center,
        child: Text(
          action.title,
          style: TextStyle(
            fontSize: 16,
            fontWeight: action.isDefault ? FontWeight.w700 : FontWeight.w500,
            letterSpacing: -0.3,
            color: textColor,
          ),
        ),
      ),
    );
  }
}
