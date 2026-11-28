import '../errors/app_failure.dart';

sealed class ViewState<T> {
  const ViewState();
}

final class ViewLoading<T> extends ViewState<T> {
  const ViewLoading();
}

final class ViewEmpty<T> extends ViewState<T> {
  const ViewEmpty({this.title, this.message});

  final String? title;
  final String? message;
}

final class ViewData<T> extends ViewState<T> {
  const ViewData(this.data);

  final T data;
}

final class ViewError<T> extends ViewState<T> {
  const ViewError(this.failure, [this.onRetry]);

  final AppFailure failure;
  final void Function()? onRetry;
}
