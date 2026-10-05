import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:ai_birthday/shared/design_system/design_system.dart';

import '../application/auth_controller.dart';
import '../domain/google_identity.dart';

class AuthBottomSheet extends ConsumerStatefulWidget {
  const AuthBottomSheet({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => const AuthBottomSheet(),
    );
  }

  @override
  ConsumerState<AuthBottomSheet> createState() => _AuthBottomSheetState();
}

class _AuthBottomSheetState extends ConsumerState<AuthBottomSheet>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;

  // Phone OTP state
  final TextEditingController _phoneController = TextEditingController();
  final TextEditingController _phoneOtpController = TextEditingController();
  bool _phoneOtpSent = false;

  // Email state
  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _emailOtpController = TextEditingController();
  bool _emailOtpSent = false;
  bool _isSignUp = false;

  bool _isLoading = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    _phoneController.dispose();
    _phoneOtpController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _emailOtpController.dispose();
    super.dispose();
  }

  void _onSuccess(String message) {
    Navigator.of(context).pop();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('$message 🎉'),
        backgroundColor: AppColors.accentForest,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  Future<void> _handleGoogleSignIn() async {
    HapticFeedback.lightImpact();
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final outcome = await ref.read(authControllerProvider.notifier).signIn();
    if (!mounted) return;
    setState(() => _isLoading = false);

    switch (outcome) {
      case SignInSuccess():
        _onSuccess('Successfully signed in with Google!');
      case SignInFailed(message: final msg):
        setState(() => _errorMessage = msg ?? 'Sign in failed');
      case SignInUnavailable(reason: final r):
        setState(() => _errorMessage = r ?? 'Sign in is unavailable');
    }
  }

  Future<void> _handleSendPhoneOtp() async {
    final phone = _phoneController.text.trim();
    if (phone.length < 8) {
      setState(
        () => _errorMessage =
            'Please enter a valid phone number with country code (e.g. +1 555-0199 or +91 9876543210).',
      );
      return;
    }

    HapticFeedback.lightImpact();
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final sent = await ref
        .read(authControllerProvider.notifier)
        .sendPhoneOtp(phone);
    if (!mounted) return;
    setState(() {
      _isLoading = false;
      if (sent) {
        _phoneOtpSent = true;
        _errorMessage = null;
      } else {
        _errorMessage = 'Failed sending OTP. Please check your phone number.';
      }
    });
  }

  Future<void> _handleVerifyPhoneOtp() async {
    final phone = _phoneController.text.trim();
    final otp = _phoneOtpController.text.trim();
    if (otp.length < 4) {
      setState(() => _errorMessage = 'Please enter the 6-digit OTP code.');
      return;
    }

    HapticFeedback.lightImpact();
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final outcome = await ref
        .read(authControllerProvider.notifier)
        .verifyPhoneOtp(phone, otp);
    if (!mounted) return;
    setState(() => _isLoading = false);

    switch (outcome) {
      case SignInSuccess():
        _onSuccess('Phone verified and logged in!');
      case SignInFailed(message: final msg):
        setState(() => _errorMessage = msg ?? 'Verification failed');
      case SignInUnavailable(reason: final r):
        setState(() => _errorMessage = r ?? 'Verification unavailable');
    }
  }

  Future<void> _handleSendEmailOtp() async {
    final email = _emailController.text.trim();
    if (!email.contains('@')) {
      setState(() => _errorMessage = 'Please enter a valid email address.');
      return;
    }

    HapticFeedback.lightImpact();
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final sent = await ref
        .read(authControllerProvider.notifier)
        .sendEmailOtp(email);
    if (!mounted) return;
    setState(() {
      _isLoading = false;
      if (sent) {
        _emailOtpSent = true;
        _errorMessage = null;
      } else {
        _errorMessage = 'Failed sending verification code.';
      }
    });
  }

  Future<void> _handleVerifyEmailOtp() async {
    final email = _emailController.text.trim();
    final otp = _emailOtpController.text.trim();
    if (otp.length < 4) {
      setState(() => _errorMessage = 'Please enter the 6-digit code.');
      return;
    }

    HapticFeedback.lightImpact();
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final outcome = await ref
        .read(authControllerProvider.notifier)
        .verifyEmailOtp(email, otp);
    if (!mounted) return;
    setState(() => _isLoading = false);

    switch (outcome) {
      case SignInSuccess():
        _onSuccess('Email verified and logged in!');
      case SignInFailed(message: final msg):
        setState(() => _errorMessage = msg ?? 'Verification failed');
      case SignInUnavailable(reason: final r):
        setState(() => _errorMessage = r ?? 'Verification unavailable');
    }
  }

  Future<void> _handleEmailPasswordAuth() async {
    final email = _emailController.text.trim();
    final password = _passwordController.text;

    if (email.isEmpty || !email.contains('@')) {
      setState(() => _errorMessage = 'Please enter a valid email address.');
      return;
    }
    if (password.length < 6) {
      setState(() => _errorMessage = 'Password must be at least 6 characters.');
      return;
    }

    HapticFeedback.lightImpact();
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    final notifier = ref.read(authControllerProvider.notifier);
    final outcome = _isSignUp
        ? await notifier.signUpWithEmail(email, password)
        : await notifier.signInWithEmail(email, password);

    if (!mounted) return;
    setState(() => _isLoading = false);

    switch (outcome) {
      case SignInSuccess():
        _onSuccess(
          _isSignUp
              ? 'Account created and signed in!'
              : 'Welcome back! Signed in.',
        );
      case SignInFailed(message: final msg):
        setState(() => _errorMessage = msg ?? 'Authentication failed');
      case SignInUnavailable(reason: final r):
        setState(() => _errorMessage = r ?? 'Authentication unavailable');
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final bottomInset = MediaQuery.viewInsetsOf(context).bottom;

    return Container(
      decoration: BoxDecoration(
        color: theme.scaffoldBackgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 16,
        bottom: bottomInset + 20,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 44,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.grey.withValues(alpha: 0.3),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),
            Text(
              'Sign In & Cloud Sync',
              style: theme.textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.bold,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 6),
            Text(
              'Securely sync birthdays across devices and unlock cloud backups.',
              style: TextStyle(fontSize: 13, color: Colors.grey[600]),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),

            // Tab bar for Google, Phone OTP, Email
            Container(
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest.withValues(
                  alpha: 0.5,
                ),
                borderRadius: BorderRadius.circular(12),
              ),
              padding: const EdgeInsets.all(4),
              child: TabBar(
                controller: _tabController,
                indicatorSize: TabBarIndicatorSize.tab,
                dividerColor: Colors.transparent,
                indicator: BoxDecoration(
                  color: theme.colorScheme.primaryContainer,
                  borderRadius: BorderRadius.circular(8),
                ),
                labelColor: theme.colorScheme.onPrimaryContainer,
                unselectedLabelColor: theme.colorScheme.onSurfaceVariant,
                labelStyle: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 13,
                ),
                tabs: const [
                  Tab(text: 'Google'),
                  Tab(text: 'Phone OTP'),
                  Tab(text: 'Email'),
                ],
              ),
            ),
            const SizedBox(height: 16),

            if (_errorMessage != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.red.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Colors.red.withValues(alpha: 0.3)),
                ),
                child: Row(
                  children: [
                    const Icon(
                      Icons.error_outline,
                      color: Colors.red,
                      size: 20,
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _errorMessage!,
                        style: const TextStyle(color: Colors.red, fontSize: 13),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),
            ],

            SizedBox(
              height: 240,
              child: TabBarView(
                controller: _tabController,
                children: [
                  // Tab 0: Google Sign-In
                  _buildGoogleTab(theme),

                  // Tab 1: Phone OTP
                  _buildPhoneOtpTab(theme),

                  // Tab 2: Email & Password / OTP
                  _buildEmailTab(theme),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildGoogleTab(ThemeData theme) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Icon(
          Icons.account_circle_outlined,
          size: 48,
          color: AppColors.primaryTerracotta,
        ),
        const SizedBox(height: 12),
        const Text(
          'Continue with your verified Google Account',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 6),
        Text(
          'One-tap sign-in with automatic cloud sync',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 12, color: Colors.grey[600]),
        ),
        const SizedBox(height: 24),
        OutlinedButton.icon(
          onPressed: _isLoading ? null : _handleGoogleSignIn,
          icon: _isLoading
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Text(
                  'G',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: AppColors.primaryTerracotta,
                  ),
                ),
          label: const Text(
            'Continue with Google',
            style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
          ),
          style: OutlinedButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 14),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
            side: BorderSide(color: theme.colorScheme.outlineVariant),
          ),
        ),
      ],
    );
  }

  Widget _buildPhoneOtpTab(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (!_phoneOtpSent) ...[
          TextField(
            controller: _phoneController,
            keyboardType: TextInputType.phone,
            decoration: InputDecoration(
              labelText: 'Mobile Phone Number',
              hintText: '+1 555-0199 or +91 9876543210',
              prefixIcon: const Icon(Icons.phone_outlined),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
              ),
              filled: true,
              fillColor: theme.colorScheme.surfaceContainerHighest.withValues(
                alpha: 0.3,
              ),
            ),
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: _isLoading ? null : _handleSendPhoneOtp,
            icon: _isLoading
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                      color: Colors.white,
                      strokeWidth: 2,
                    ),
                  )
                : const Icon(Icons.send_outlined),
            label: const Text(
              'Send Verification Code (OTP)',
              style: TextStyle(fontWeight: FontWeight.bold),
            ),
            style: FilledButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
          ),
        ] else ...[
          TextField(
            controller: _phoneOtpController,
            keyboardType: TextInputType.number,
            maxLength: 6,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 22,
              letterSpacing: 8,
              fontWeight: FontWeight.bold,
            ),
            decoration: InputDecoration(
              labelText: '6-Digit Verification Code',
              hintText: '123456',
              prefixIcon: const Icon(Icons.lock_clock_outlined),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
              ),
              filled: true,
              fillColor: theme.colorScheme.surfaceContainerHighest.withValues(
                alpha: 0.3,
              ),
            ),
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: _isLoading ? null : _handleVerifyPhoneOtp,
            style: FilledButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            child: _isLoading
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      color: Colors.white,
                      strokeWidth: 2,
                    ),
                  )
                : const Text(
                    'Verify & Sign In',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                  ),
          ),
          TextButton(
            onPressed: () => setState(() => _phoneOtpSent = false),
            child: const Text('Change Phone Number'),
          ),
        ],
      ],
    );
  }

  Widget _buildEmailTab(ThemeData theme) {
    if (_emailOtpSent) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextField(
            controller: _emailOtpController,
            keyboardType: TextInputType.number,
            maxLength: 6,
            textAlign: TextAlign.center,
            style: const TextStyle(
              fontSize: 22,
              letterSpacing: 8,
              fontWeight: FontWeight.bold,
            ),
            decoration: InputDecoration(
              labelText: '6-Digit Email Code',
              hintText: '123456',
              prefixIcon: const Icon(Icons.mark_email_read_outlined),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(12),
              ),
              filled: true,
              fillColor: theme.colorScheme.surfaceContainerHighest.withValues(
                alpha: 0.3,
              ),
            ),
          ),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: _isLoading ? null : _handleVerifyEmailOtp,
            style: FilledButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            child: _isLoading
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      color: Colors.white,
                      strokeWidth: 2,
                    ),
                  )
                : const Text(
                    'Verify & Sign In',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                  ),
          ),
          TextButton(
            onPressed: () => setState(() => _emailOtpSent = false),
            child: const Text('Use Password Instead'),
          ),
        ],
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: _emailController,
          keyboardType: TextInputType.emailAddress,
          decoration: InputDecoration(
            labelText: 'Email Address',
            prefixIcon: const Icon(Icons.email_outlined),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            filled: true,
            fillColor: theme.colorScheme.surfaceContainerHighest.withValues(
              alpha: 0.3,
            ),
          ),
        ),
        const SizedBox(height: 10),
        TextField(
          controller: _passwordController,
          obscureText: true,
          decoration: InputDecoration(
            labelText: 'Password',
            prefixIcon: const Icon(Icons.lock_outline),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            filled: true,
            fillColor: theme.colorScheme.surfaceContainerHighest.withValues(
              alpha: 0.3,
            ),
          ),
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: FilledButton(
                onPressed: _isLoading ? null : _handleEmailPasswordAuth,
                style: FilledButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: _isLoading
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                          color: Colors.white,
                          strokeWidth: 2,
                        ),
                      )
                    : Text(
                        _isSignUp ? 'Sign Up' : 'Sign In',
                        style: const TextStyle(fontWeight: FontWeight.bold),
                      ),
              ),
            ),
            const SizedBox(width: 8),
            OutlinedButton(
              onPressed: _isLoading ? null : _handleSendEmailOtp,
              style: OutlinedButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  vertical: 12,
                  horizontal: 12,
                ),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: const Text('Send OTP Code'),
            ),
          ],
        ),
        Center(
          child: TextButton(
            onPressed: () => setState(() => _isSignUp = !_isSignUp),
            child: Text(
              _isSignUp
                  ? 'Already have an account? Sign In'
                  : "Don't have an account? Sign Up",
              style: const TextStyle(fontSize: 12),
            ),
          ),
        ),
      ],
    );
  }
}
