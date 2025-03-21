package com.hifnawy.caffeinate.view

import android.animation.Animator
import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.NumberPicker
import androidx.annotation.IdRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.animation.addListener
import androidx.core.view.forEach
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.stephenvinouze.materialnumberpickercore.MaterialNumberPicker
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.hifnawy.caffeinate.CaffeinateApplication
import com.hifnawy.caffeinate.R
import com.hifnawy.caffeinate.databinding.DialogSetCustomTimeoutBinding
import com.hifnawy.caffeinate.databinding.FragmentChooseTimeoutsBinding
import com.hifnawy.caffeinate.utils.DurationExtensionFunctions.toLocalizedFormattedTime
import com.hifnawy.caffeinate.utils.IntExtensionFunctions.dp
import com.hifnawy.caffeinate.utils.ViewExtensionFunctions.viewHeight
import com.hifnawy.caffeinate.utils.ViewExtensionFunctions.windowHeight
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Fragment that allows selection of timeout durations.
 *
 * This fragment provides a user interface for selecting timeout durations
 * using a list of checkboxes. It is presented as a bottom sheet dialog
 * and allows users to add, remove, and save timeout durations. The fragment
 * communicates the selected timeouts back to the hosting application via a callback.
 *
 * @author AbdAlMoniem AlHifnawy
 *
 * @see CheckBoxAdapter
 * @see BottomSheetBehavior
 * @see BottomSheetDialogFragment
 */
class TimeoutsSelectionFragment : BottomSheetDialogFragment() {

    /**
     * A lazy delegate that inflates the layout of this fragment and stores the result in a
     * [FragmentChooseTimeoutsBinding] instance.
     *
     * This property is initialized the first time it is accessed, and the result is reused
     * for all subsequent calls.
     *
     * @return [FragmentChooseTimeoutsBinding] The inflated layout of the fragment, wrapped in a [FragmentChooseTimeoutsBinding] instance.
     */
    private val binding by lazy { FragmentChooseTimeoutsBinding.inflate(layoutInflater) }

    /**
     * Lazily retrieves the application instance.
     *
     * This property is initialized upon first access and provides the [CaffeinateApplication]
     * instance, which can be used to access application-wide resources and functionality.
     *
     * @return [CaffeinateApplication] The application instance.
     */
    private val caffeinateApplication: CaffeinateApplication by lazy { binding.root.context.applicationContext as CaffeinateApplication }

    /**
     * A lazy delegate that creates a new instance of [CheckBoxAdapter] with the specified timeout check boxes and on-items-changed listener.
     *
     * The adapter is created with the list of [CheckBoxItem]s from [CaffeinateApplication.timeoutCheckBoxes], and an on-items-changed listener that
     * is notified when the list of check boxes changes. The listener is responsible for updating the state of the menu items in the toolbar based
     * on whether the list is empty or not.
     *
     * @return [CheckBoxAdapter] The adapter for the RecyclerView of timeout check boxes.
     */
    private val checkBoxAdapter by lazy {
        CheckBoxAdapter(caffeinateApplication.timeoutCheckBoxes) { checkBoxItems ->
            val isEmpty = checkBoxItems.isEmpty()

            with(binding.toolbar.menu) {
                findItem(R.id.save_selected).isVisible = !isEmpty

                findItem(R.id.add_infinite_timeout).isVisible = checkBoxItems.find { it.duration.isInfinite() } == null
            }
        }.apply { onItemsSelectionChangedListener = CheckBoxAdapter.OnItemsSelectionChangedListener(::onItemSelectionChanged) }
    }

    /**
     * A lazy delegate that initializes and stores a list of [MenuItemBehavior] instances.
     *
     * This property is initialized the first time it is accessed, and the result is reused
     * for all subsequent calls. The list of [MenuItemBehavior]s is initialized with the
     * following items:
     * - A [MenuItemBehavior] with the ID [R.id.remove_timeouts] that calls the [removeTimeouts]
     *   function when the menu item is clicked.
     * - A [MenuItemBehavior] with the ID [R.id.add_timeout] that calls the [addTimeout]
     *   function when the menu item is clicked.
     * - A [MenuItemBehavior] with the ID [R.id.save_selected] that calls the [saveSelectedTimeouts]
     *   function when the menu item is clicked.
     *
     * @return [List] A list of [MenuItemBehavior]s that are used to define the behavior of
     * the menu items in the toolbar.
     */
    private val menuItemBehaviors by lazy {
        mutableListOf(
                MenuItemBehavior(R.id.remove_timeouts, ::removeTimeouts),
                MenuItemBehavior(R.id.change_selection, ::changeAllTimeoutsSelection),
                MenuItemBehavior(R.id.add_infinite_timeout, ::addInfiniteTimeout),
                MenuItemBehavior(R.id.add_timeout, ::addTimeout),
                MenuItemBehavior(R.id.save_selected, ::saveSelectedTimeouts)
        )
    }

    /**
     * A property that is initialized by the [onCreateDialog] function, which sets it to the
     * [BottomSheetBehavior] instance of the [BottomSheetDialog] that is created by the
     * [onCreateDialog] function.
     *
     * This property is used to control the state of the bottom sheet dialog, such as expanding
     * or collapsing the dialog. It is also used to set the peek height of the dialog.
     *
     * @see onCreateDialog
     * @see BottomSheetBehavior
     * @see BottomSheetDialog
     */
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    /**
     * The minimum height of the drag handle in display pixels (DP).
     *
     * This property is used to control the height of the drag handle in the [BottomSheetDialog] that is created by the [onCreateDialog]
     * function. The height of the drag handle is animated from [minDragHandleHeight] to [maxDragHandleHeight] and vice versa to create
     * a smooth transition between the collapsed and expanded states of the dialog.
     *
     * @see maxDragHandleHeight
     * @see onCreateDialog
     * @see BottomSheetBehavior
     * @see BottomSheetDialog
     */
    private val minDragHandleHeight = 0.dp

    /**
     * The maximum height of the drag handle in display pixels (DP).
     *
     * This property is used to control the height of the drag handle in the [BottomSheetDialog] that is created by the [onCreateDialog]
     * function. The height of the drag handle is animated from [minDragHandleHeight] to [maxDragHandleHeight] and vice versa to create
     * a smooth transition between the collapsed and expanded states of the dialog.
     *
     * @see maxDragHandleHeight
     * @see onCreateDialog
     * @see BottomSheetBehavior
     * @see BottomSheetDialog
     */
    private val maxDragHandleHeight = 48.dp

    /**
     * The minimum height of the toolbar in display pixels (DP).
     *
     * This property is used to control the height of the toolbar in the [BottomSheetDialog] that is created by the [onCreateDialog]
     * function. The height of the toolbar is animated from [minToolbarHeight] to [maxToolbarHeight] and vice versa to create a smooth transition
     * between the collapsed and expanded states of the dialog.
     *
     * @see maxToolbarHeight
     * @see onCreateDialog
     * @see BottomSheetBehavior
     * @see BottomSheetDialog
     */
    private val minToolbarHeight = 0.dp

    /**
     * The maximum height of the toolbar in display pixels (DP).
     *
     * This property is used to control the height of the toolbar in the [BottomSheetDialog] that is created by the [onCreateDialog]
     * function. The height of the toolbar is animated from [minToolbarHeight] to [maxToolbarHeight] and vice versa to create a smooth transition
     * between the collapsed and expanded states of the dialog.
     *
     * @see minToolbarHeight
     * @see onCreateDialog
     * @see BottomSheetBehavior
     * @see BottomSheetDialog
     */
    private val maxToolbarHeight = 64.dp

    /**
     * The minimum size of the toolbar title text in scaled pixels (SP).
     *
     * This property is used to control the size of the toolbar title text in the [BottomSheetDialog] that is created by the [onCreateDialog]
     * function. The text size is animated from [minToolbarTitleSize] to [maxToolbarTitleSize] and vice versa to create a smooth transition
     * between the collapsed and expanded states of the dialog.
     *
     * @see maxToolbarTitleSize
     * @see onCreateDialog
     * @see BottomSheetBehavior
     * @see BottomSheetDialog
     */
    private val minToolbarTitleSize = 10f

    /**
     * The maximum size of the toolbar title text in scaled pixels (SP).
     *
     * This property is used to control the size of the toolbar title text in the [BottomSheetDialog] that is created by the [onCreateDialog]
     * function. The text size is animated from [minToolbarTitleSize] to [maxToolbarTitleSize] and vice versa to create a smooth transition
     * between the collapsed and expanded states of the dialog.
     *
     * @see maxToolbarTitleSize
     * @see onCreateDialog
     * @see BottomSheetBehavior
     * @see BottomSheetDialog
     */
    private val maxToolbarTitleSize = 30f

    /**
     * A callback that will be invoked when the user selects a timeout duration.
     *
     * This callback will be invoked when the user selects a timeout duration and clicks the "Save"
     * button. It will receive a list of [CheckBoxItem] as an argument that contains the selected
     * timeout durations.
     *
     * @see CheckBoxItem
     * @see saveSelectedTimeouts
     */
    var selectionCallback: ((List<CheckBoxItem>) -> Unit)? = null

    /**
     * Called to have the fragment instantiate its user interface view.
     * This is optional, and non-graphical fragments can return null. This will be called between
     * [onCreate] and [onViewCreated].
     *
     * A default View can be returned by calling [Fragment][androidx.fragment.app.Fragment]
     * in your constructor. Otherwise, this method returns null.
     *
     * It is recommended to **only** inflate the layout in this method and move
     * logic that operates on the returned View to [.onViewCreated].
     *
     * If you return a View from here, you will later be called in
     * [.onDestroyView] when the view is being released.
     *
     * @param inflater The LayoutInflater object that can be used to inflate
     * any views in the fragment,
     * @param container If non-null, this is the parent view that the fragment's
     * UI should be attached to.  The fragment should not add the view itself,
     * but this can be used to generate the LayoutParams of the view.
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     *
     * @return Return the View for the fragment's UI, or null.
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) = binding.root

    /**
     * Called immediately after [onCreateView]
     * has returned, but before any saved state has been restored in to the view.
     * This gives subclasses a chance to initialize themselves once
     * they know their view hierarchy has been completely created.  The fragment's
     * view hierarchy is not however attached to its parent at this point.
     *
     * @param view The View returned by [onCreateView].
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        dragHandle.viewHeight = maxDragHandleHeight
        toolbar.viewHeight = minToolbarHeight

        with(toolbar.menu) {
            findItem(R.id.remove_timeouts).isVisible = false
            findItem(R.id.add_infinite_timeout).isVisible = checkBoxAdapter.checkBoxItems.find { it.duration.isInfinite() } == null
            findItem(R.id.change_selection).apply {
                isVisible = false
                title = getString(R.string.select_all_timeouts)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) tooltipText = title
            }

            forEach { menuItem ->
                val onMenuItemClick = menuItemBehaviors.find { it.menuItemId == menuItem.itemId }?.onMenuItemClick

                menuItem.setOnMenuItemClickListener(onMenuItemClick)
            }
        }

        toolbar.setNavigationOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        timeoutsRecyclerView.layoutManager = LinearLayoutManager(root.context)
        timeoutsRecyclerView.adapter = checkBoxAdapter
    }

    /**
     * Called to create a dialog for the fragment.
     *
     * This method creates a new instance of [BottomSheetDialog] using the current context
     * and applies configuration settings to customize its behavior and appearance.
     *
     * @param savedInstanceState [Bundle] The last saved instance state of the Fragment, or null if this
     * is a freshly created Fragment.
     *
     * @return [BottomSheetDialog] The created dialog instance with applied custom settings.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?) = BottomSheetDialog(binding.root.context).apply {
        setOnShowListener { dialogInterface ->
            val dialog = (dialogInterface as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            dialog?.layoutParams?.height = binding.root.windowHeight

            bottomSheetBehavior = behavior.apply {
                isFitToContents = true
                dismissWithAnimation = true
                peekHeight = binding.root.windowHeight * 2 / 5
                state = BottomSheetBehavior.STATE_COLLAPSED

                addBottomSheetCallback(BottomSheetBehaviorCallback())
            }
        }
    }

    /**
     * Removes all checked timeout items from the list.
     *
     * This method is called when the user clicks the "Remove selected" menu item.
     * It removes all checked [CheckBoxItem]s from the list of available timeout
     * items.
     *
     * @param menuItem [MenuItem] The menu item that was clicked.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun removeTimeouts(menuItem: MenuItem): Boolean {
        binding.root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        checkBoxAdapter.removeSelectedCheckBoxes()

        return true
    }

    /**
     * Toggles the selection state of all timeout items in the list.
     *
     * This method is called when the user clicks the "Select all" or "Deselect all" menu item.
     * It toggles the selection state of all [CheckBoxItem]s in the list of available timeout
     * items.
     *
     * @param menuItem [MenuItem] The menu item that was clicked.
     *
     * @return [Boolean] `true` if the menu item was successfully handled, `false` otherwise.
     *
     * @see MenuItem
     * @see CheckBoxItem
     */
    @Suppress("UNUSED_PARAMETER")
    private fun changeAllTimeoutsSelection(menuItem: MenuItem): Boolean {
        binding.root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        checkBoxAdapter.changeAllCheckBoxesSelection()

        return true
    }

    /**
     * Called when the selection state of one or more [CheckBoxItem]s changes.
     *
     * This method is called whenever the selection state of one or more [CheckBoxItem]s changes.
     * It is called by the [CheckBoxAdapter] when the user selects or deselects one or more
     * [CheckBoxItem]s.
     *
     * The method updates the toolbar menu items to reflect the current selection state.
     * It shows or hides the "Remove" menu item based on whether any items are selected, and
     * changes the icon of the "Select all" or "Deselect all" menu item based on whether any
     * items are selected.
     *
     * @param selectedItems [List] The list of currently selected [CheckBoxItem]s.
     * @param isSelecting [Boolean] Whether the user is currently selecting or deselecting items.
     * @param isAllSelected [Boolean] Whether all items are currently selected.
     *
     * @see CheckBoxAdapter
     * @see CheckBoxItem
     * @see MenuItem
     */
    private fun onItemSelectionChanged(selectedItems: List<CheckBoxItem>, isSelecting: Boolean, isAllSelected: Boolean) = with(binding.toolbar.menu) {
        findItem(R.id.remove_timeouts).apply {
            isVisible = isSelecting

            title = binding.root.context.resources.getQuantityString(R.plurals.remove_timeout, selectedItems.size, selectedItems.size)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) tooltipText = title
        }
        findItem(R.id.change_selection).apply {
            isVisible = isSelecting
            title = when {
                isAllSelected -> getString(R.string.deselect_all_timeouts)
                else          -> getString(R.string.select_all_timeouts)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) tooltipText = title

            icon = when {
                isAllSelected -> AppCompatResources.getDrawable(binding.root.context, R.drawable.deselect_all_icon)
                else          -> AppCompatResources.getDrawable(binding.root.context, R.drawable.select_all_icon)
            }
        }
    }

    /**
     * Adds a new timeout to the list of available timeouts.
     *
     * This method is called when the user clicks the "Add" menu item. It shows a
     * dialog to the user to let them choose a custom timeout, and when the user
     * sets a value, it adds a new [CheckBoxItem] to the list of available
     * timeouts with the chosen duration.
     *
     * @param menuItem [MenuItem] The menu item that was clicked.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun addTimeout(menuItem: MenuItem): Boolean {
        binding.root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        showSetCustomTimeoutDialog { timeout ->
            if (checkBoxAdapter.checkBoxItems.find { it.duration == timeout } != null) {
                Snackbar.make(
                        binding.root,
                        getString(
                                R.string.timeout_already_exists,
                                timeout.toLocalizedFormattedTime(caffeinateApplication.localizedApplicationContext)
                        ),
                        Snackbar.LENGTH_SHORT
                ).run {
                    setAction(getString(R.string.ok)) { view ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        dismiss()
                    }
                    show()
                }
                return@showSetCustomTimeoutDialog
            }

            checkBoxAdapter.addCheckBox(
                    CheckBoxItem(
                            text = timeout.toLocalizedFormattedTime(caffeinateApplication.localizedApplicationContext),
                            isChecked = true,
                            isEnabled = true,
                            duration = timeout
                    )
            )
        }

        return true
    }

    /**
     * Adds an infinite timeout to the list of available timeouts.
     *
     * This method is called when the user selects the "Add Infinite" menu item.
     * It performs a haptic feedback, creates a new [CheckBoxItem] with an infinite duration,
     * and adds it to the list of available timeouts.
     *
     * @param menuItem [MenuItem] The menu item that was clicked.
     * @return [Boolean] `true` if the menu item was successfully handled, `false` otherwise.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun addInfiniteTimeout(menuItem: MenuItem): Boolean {
        binding.root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        if (checkBoxAdapter.checkBoxItems.find { it.duration.isInfinite() } != null) {
            Snackbar.make(
                    binding.root,
                    getString(
                            R.string.timeout_already_exists,
                            Duration.INFINITE.toLocalizedFormattedTime(caffeinateApplication.localizedApplicationContext)
                    ),
                    Snackbar.LENGTH_SHORT
            ).run {
                setAction(getString(R.string.ok)) { view ->
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    dismiss()
                }
                show()
            }

            return false
        }

        checkBoxAdapter.addCheckBox(
                CheckBoxItem(
                        text = Duration.INFINITE.toLocalizedFormattedTime(caffeinateApplication.localizedApplicationContext),
                        isChecked = true,
                        isEnabled = true,
                        duration = Duration.INFINITE
                )
        )

        return true
    }

    /**
     * Saves the selected timeout durations.
     *
     * This method is called when the user selects the "Save" menu item. It performs
     * a haptic feedback, invokes the selection callback with the list of selected
     * [CheckBoxItem]s, and hides the bottom sheet dialog.
     *
     * @param menuItem [MenuItem] The menu item that was clicked.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun saveSelectedTimeouts(menuItem: MenuItem): Boolean {
        binding.root.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        selectionCallback?.invoke(checkBoxAdapter.checkBoxItems)

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        return true
    }

    /**
     * Shows a dialog to the user to let them choose a custom timeout.
     *
     * @param valueSetCallback [(timeout: Duration) -> Unit][valueSetCallback] a callback that will be called when the user sets a value; the callback
     * will be passed the number of hours, minutes and
     * seconds that the user has chosen
     */
    private fun showSetCustomTimeoutDialog(valueSetCallback: (timeout: Duration) -> Unit) {
        with(binding) {
            val dialogBinding = DialogSetCustomTimeoutBinding.inflate(LayoutInflater.from(root.context))
            val dialog = MaterialAlertDialogBuilder(root.context)
                .setView(dialogBinding.root)
                .create()
                .apply {
                    setCancelable(false)
                    window?.setLayout(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

            with(dialogBinding) {
                val onNumberPickerAnimationStart = { _: Animator -> dialogButtonRandomTimeout.isEnabled = false }
                val onNumberPickerAnimationEnd = { _: Animator -> dialogButtonRandomTimeout.isEnabled = true }

                hoursNumberPicker.setFormatter { value -> "%02d".format(value) }
                minutesNumberPicker.setFormatter { value -> "%02d".format(value) }
                secondsNumberPicker.setFormatter { value -> "%02d".format(value) }

                NumberPicker.OnValueChangeListener { numberPicker, _, _ ->
                    numberPicker.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }.run {
                    hoursNumberPicker.setOnValueChangedListener(this)
                    minutesNumberPicker.setOnValueChangedListener(this)
                    secondsNumberPicker.setOnValueChangedListener(this)
                }

                hoursNumberPicker.animateRandom(onAnimationStart = onNumberPickerAnimationStart, onAnimationEnd = onNumberPickerAnimationEnd)
                minutesNumberPicker.animateRandom(onAnimationStart = onNumberPickerAnimationStart, onAnimationEnd = onNumberPickerAnimationEnd)
                secondsNumberPicker.animateRandom(onAnimationStart = onNumberPickerAnimationStart, onAnimationEnd = onNumberPickerAnimationEnd)

                dialogButtonRandomTimeout.setOnClickListener { buttonView ->
                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                    hoursNumberPicker.animateFrom(
                            hoursNumberPicker.value,
                            onAnimationStart = onNumberPickerAnimationStart,
                            onAnimationEnd = onNumberPickerAnimationEnd
                    )
                    minutesNumberPicker.animateFrom(
                            minutesNumberPicker.value,
                            onAnimationStart = onNumberPickerAnimationStart,
                            onAnimationEnd = onNumberPickerAnimationEnd
                    )
                    secondsNumberPicker.animateFrom(
                            secondsNumberPicker.value,
                            onAnimationStart = onNumberPickerAnimationStart,
                            onAnimationEnd = onNumberPickerAnimationEnd
                    )
                }

                dialogButtonCancel.setOnClickListener { buttonView ->
                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

                    dialog.dismiss()
                }

                dialogButtonOk.setOnClickListener { buttonView ->
                    buttonView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    val timeout = when {
                        hoursNumberPicker.value + minutesNumberPicker.value + secondsNumberPicker.value == 0 -> Duration.INFINITE
                        else                                                                                 ->
                            hoursNumberPicker.value.hours + minutesNumberPicker.value.minutes + secondsNumberPicker.value.seconds
                    }

                    valueSetCallback(timeout)

                    dialog.dismiss()
                }
            }

            dialog.show()
        }
    }

    /**
     * Animates the value of this [MaterialNumberPicker] from the closest boundary (either [maxValue][MaterialNumberPicker.getMaxValue] or
     * [minValue][MaterialNumberPicker.getMinValue]) to a value chosen randomly between [minValue][MaterialNumberPicker.getMinValue] and
     * [maxValue][MaterialNumberPicker.getMaxValue]. if it is closer to that boundary.
     *
     * @param animationDuration [Long] The duration of the animation in milliseconds. Defaults to `1000L`.
     * @param onAnimationStart [(animator: Animator) -> Unit][onAnimationStart] A callback that will be called when the animation starts.
     * Defaults to an empty function.
     * @param onAnimationEnd [(animator: Animator) -> Unit][onAnimationEnd] A callback that will be called when the animation ends.
     * Defaults to an empty function.
     */
    private fun MaterialNumberPicker.animateRandom(
            animationDuration: Long = 1000L,
            onAnimationStart: (animator: Animator) -> Unit = {},
            onAnimationEnd: (animator: Animator) -> Unit = {},
    ) {
        val toValue = Random.nextInt(minValue, maxValue)

        val (startValue, endValue) = when {
            // fromValue is closer to minValue
            abs(toValue - minValue) < abs(toValue - maxValue) -> maxValue to toValue
            // fromValue is closer to maxValue
            else                                              -> minValue to toValue
        }

        ValueAnimator.ofInt(startValue, endValue).apply {
            addUpdateListener { animator ->
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                value = animator.animatedValue as Int
            }

            addListener(onStart = onAnimationStart, onEnd = onAnimationEnd)

            duration = animationDuration

            start()
        }
    }

    /**
     * Animates the value of this [MaterialNumberPicker] from the given [fromValue] to a random value that's at least at a distance of
     *  -\+(([minValue][MaterialNumberPicker.getMinValue] + [maxValue][MaterialNumberPicker.getMaxValue]) / 2) from the current
     * [value][MaterialNumberPicker.getValue].
     *
     * @param fromValue [Int] The starting value of the animation.
     * @param animationDuration [Long] The duration of the animation in milliseconds. Defaults to `1000L`.
     * @param onAnimationStart [(animator: Animator) -> Unit][onAnimationStart] A callback that will be called when the animation starts.
     * Defaults to an empty function.
     * @param onAnimationEnd [(animator: Animator) -> Unit][onAnimationEnd] A callback that will be called when the animation ends.
     * Defaults to an empty function.
     */
    private fun MaterialNumberPicker.animateFrom(
            fromValue: Int,
            animationDuration: Long = 1000L,
            onAnimationStart: (animator: Animator) -> Unit = {},
            onAnimationEnd: (animator: Animator) -> Unit = {},
    ) {
        val minDistance = (minValue + maxValue) / 2

        val (startValue, endValue) = when {
            // fromValue is closer to minValue
            abs(value - minValue) < abs(value - maxValue) -> fromValue to Random.nextInt(minDistance, maxValue)
            // fromValue is closer to maxValue
            else                                          -> fromValue to Random.nextInt(minValue, minDistance)
        }

        ValueAnimator.ofInt(startValue, endValue).apply {
            addUpdateListener { animator ->
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                value = animator.animatedValue as Int
            }

            addListener(onStart = onAnimationStart, onEnd = onAnimationEnd)

            duration = animationDuration

            start()
        }
    }

    /**
     * Data class that represents a behavior associated with a menu item.
     *
     * This class is used to associate a behavior with a menu item in the overflow menu of the [TimeoutsSelectionFragment].
     * It contains the resource id of the menu item and a callback that will be invoked when the user clicks the menu item.
     *
     * @property menuItemId [Int] The resource id of the menu item.
     * @property onMenuItemClick [(MenuItem) -> Unit] A callback that will be invoked when the user clicks the menu item.
     */
    private data class MenuItemBehavior(
            /**
             * The resource id of the menu item.
             */
            @IdRes
            val menuItemId: Int,

            /**
             * A callback that will be invoked when the user clicks the menu item.
             */
            val onMenuItemClick: (MenuItem) -> Boolean
    )

    /**
     * A callback that will be invoked when the bottom sheet changes its state.
     *
     * This class implements the [BottomSheetBehavior.BottomSheetCallback] interface and is used to observe state changes of the
     * bottom sheet in the [TimeoutsSelectionFragment].
     *
     * @author AbdAlMoniem AlHifnawy
     * @see BottomSheetBehavior.BottomSheetCallback
     */
    private inner class BottomSheetBehaviorCallback : BottomSheetBehavior.BottomSheetCallback() {

        /**
         * Called when the bottom sheet changes its state.
         *
         * @param bottomSheet The bottom sheet view.
         * @param newState The new state. This will be one of:
         * - [BottomSheetBehavior.STATE_EXPANDED]
         * - [BottomSheetBehavior.STATE_HALF_EXPANDED].
         * - [BottomSheetBehavior.STATE_COLLAPSED]
         * - [BottomSheetBehavior.STATE_DRAGGING]
         * - [BottomSheetBehavior.STATE_SETTLING]
         * - [BottomSheetBehavior.STATE_HIDDEN]
         */
        override fun onStateChanged(bottomSheet: View, newState: Int) = with(binding) {
            dragHandle.viewHeight = when (newState) {
                BottomSheetBehavior.STATE_EXPANDED  -> minDragHandleHeight
                BottomSheetBehavior.STATE_COLLAPSED -> maxDragHandleHeight
                else                                -> dragHandle.measuredHeight
            }
            toolbar.viewHeight = when (newState) {
                BottomSheetBehavior.STATE_EXPANDED  -> maxToolbarHeight
                BottomSheetBehavior.STATE_COLLAPSED -> minToolbarHeight
                else                                -> toolbar.measuredHeight
            }
            toolbarTitle.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED  -> maxToolbarTitleSize
                        BottomSheetBehavior.STATE_COLLAPSED -> minToolbarTitleSize
                        else                                -> toolbarTitle.textSize
                    }
            )
        }

        /**
         * Called when the bottom sheet is being dragged.
         *
         * @param bottomSheet The bottom sheet view.
         * @param slideOffset The new offset of this bottom sheet within [-1,1] range. Offset increases
         * as this bottom sheet is moving upward. From 0 to 1 the sheet is between collapsed and
         * expanded states and from -1 to 0 it is between hidden and collapsed states.
         */
        override fun onSlide(bottomSheet: View, slideOffset: Float) = with(binding) {
            dragHandle.viewHeight = when {
                slideOffset > 0f -> maxDragHandleHeight + ((minDragHandleHeight - maxDragHandleHeight) * slideOffset).toInt()
                else             -> maxDragHandleHeight
            }
            toolbar.viewHeight = when {
                slideOffset > 0f -> minToolbarHeight + ((maxToolbarHeight - minToolbarHeight) * slideOffset).toInt()
                else             -> minToolbarHeight
            }
            toolbarTitle.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    when {
                        slideOffset > 0f -> minToolbarTitleSize + ((maxToolbarTitleSize - minToolbarTitleSize) * slideOffset)
                        else             -> minToolbarTitleSize
                    }
            )
            toolbar.alpha = slideOffset
            toolbarTitle.alpha = slideOffset
        }
    }

    /**
     * A companion object for the [TimeoutsSelectionFragment].
     *
     * This companion object provides a factory method for creating an instance of the [TimeoutsSelectionFragment].
     * It also contains a data class that represents a behavior associated with a menu item.
     *
     * @author AbdAlMoniem AlHifnawy
     */
    companion object {

        /**
         * Creates an instance of the [TimeoutsSelectionFragment].
         *
         * @return [TimeoutsSelectionFragment] An instance of the [TimeoutsSelectionFragment].
         */
        val newInstance
            get() = TimeoutsSelectionFragment()
    }
}