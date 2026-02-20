# Work View widget – why it might not display anything

## 1. **No images in the widget layout**
App widgets use `RemoteViews`, which do **not** support all views and resources. To avoid a blank or broken widget:

- **Do not** add `ImageView`, `ImageButton`, or any `android:background="@drawable/..."` or PNG references to `res/layout/widget_workview.xml`.
- The layout uses only `LinearLayout`, `View`, and `TextView` with solid colors (`#f0f4f8`, `@android:color/white`). Keep it that way.

If you add a drawable/PNG and the widget goes blank, remove that reference and rebuild.

## 2. **No data yet**
The widget shows the next 3 jobs from either:

- **Cache** – after you open **Work View** and today’s jobs are loaded, they are cached so the widget can show them even when logged out.
- **Logged in** – if there is no cache for today, it uses the next 3 upcoming jobs from local storage (only when the user has logged in at least once).

So if the widget is “empty” with placeholders:

- Open **Work View** once (while logged in) so today’s jobs are cached, or  
- Log in and ensure there are upcoming jobs in Work View.

Then the widget should show “Work View”, the date, and up to 3 job lines (or “No jobs today” / “Open Work View to load” if there are none).

## 3. **Widget not updating**
If the widget was added a long time ago or the app was updated:

- **Remove** the widget from the home screen (long-press → Remove).
- **Add** the “Work View” widget again from the widget picker.

This forces `onUpdate` to run again. You can also open Work View in the app to trigger a refresh.

## 4. **Check Logcat**
If the widget still shows nothing, look for errors when the widget updates:

- Tag: `WorkViewWidget`
- Message: `Widget update failed (check layout has no PNG/drawable): ...`

That indicates an exception during update (e.g. unsupported view or resource in the layout). Fix the layout (remove drawables/PNGs) and try again.
