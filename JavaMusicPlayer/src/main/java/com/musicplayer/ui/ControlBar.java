package com.musicplayer.ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

public final class ControlBar extends JPanel {
    public enum PlayMode { SEQUENCE, REPEAT_ONE, SHUFFLE }

    private final Supplier<Color> accentSupplier;
    private final ProgressSlider progress;
    private final VolumeSlider volume;
    private final JLabel elapsed = new JLabel("0:00");
    private final JLabel total = new JLabel("0:00");
    private final JLabel modeText = new JLabel("顺序播放");
    private final IconButton modeButton;
    private final IconButton previousButton;
    private final IconButton playButton;
    private final IconButton stopButton;
    private final IconButton nextButton;
    private final IconButton volumeButton;
    private long durationMillis;
    private int modeIndex;
    private int lastVolume = 72;
    private boolean muted;
    private Runnable onPrevious = () -> { };
    private Runnable onPlayPause = () -> { };
    private Runnable onStop = () -> { };
    private Runnable onNext = () -> { };
    private Consumer<PlayMode> onModeChanged = mode -> { };

    public ControlBar(Supplier<Color> accentSupplier) {
        this.accentSupplier = accentSupplier;
        setOpaque(false);
        setLayout(new BorderLayout(0, 9));
        setBorder(new EmptyBorder(10, 20, 13, 20));
        progress = new ProgressSlider(accentSupplier);
        volume = new VolumeSlider(accentSupplier);
        elapsed.setForeground(new Color(178, 185, 196));
        total.setForeground(new Color(178, 185, 196));
        elapsed.setFont(elapsed.getFont().deriveFont(11f));
        total.setFont(total.getFont().deriveFont(11f));
        JPanel timeline = new JPanel(new BorderLayout(12, 0));
        timeline.setOpaque(false);
        timeline.add(elapsed, BorderLayout.WEST);
        timeline.add(progress, BorderLayout.CENTER);
        timeline.add(total, BorderLayout.EAST);
        add(timeline, BorderLayout.NORTH);

        modeButton = button(IconPainter.IconType.REPEAT, 34, false, "顺序播放");
        modeButton.addActionListener(this::cycleMode);
        modeText.setForeground(new Color(190, 197, 207));
        modeText.setFont(modeText.getFont().deriveFont(11f));
        JPanel modeGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        modeGroup.setOpaque(false);
        modeGroup.add(modeButton);
        modeGroup.add(modeText);

        previousButton = button(IconPainter.IconType.PREVIOUS, 42, false, "上一首");
        playButton = button(IconPainter.IconType.PLAY, 58, true, "播放或暂停");
        stopButton = button(IconPainter.IconType.STOP, 40, false, "停止");
        nextButton = button(IconPainter.IconType.NEXT, 42, false, "下一首");
        previousButton.addActionListener(() -> onPrevious.run());
        playButton.addActionListener(() -> onPlayPause.run());
        stopButton.addActionListener(() -> onStop.run());
        nextButton.addActionListener(() -> onNext.run());

        JPanel transport = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        transport.setOpaque(false);
        transport.add(previousButton);
        transport.add(playButton);
        transport.add(stopButton);
        transport.add(nextButton);

        volumeButton = button(IconPainter.IconType.VOLUME, 34, false, "静音");
        volumeButton.addActionListener(this::toggleMute);
        volume.setChangeListener(this::volumeChanged);
        JPanel volumeGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        volumeGroup.setOpaque(false);
        volumeGroup.add(volumeButton);
        volumeGroup.add(volume);
        JPanel controls = new JPanel(new BorderLayout());
        controls.setOpaque(false);
        controls.add(modeGroup, BorderLayout.WEST);
        controls.add(transport, BorderLayout.CENTER);
        controls.add(volumeGroup, BorderLayout.EAST);
        add(controls, BorderLayout.CENTER);
        setPreferredSize(new Dimension(560, 118));

        progress.setSeekListener(value -> onSeek.accept(value));
    }

    private LongConsumer onSeek = value -> { };
    private DoubleConsumer onVolume = value -> { };

    private IconButton button(IconPainter.IconType type, int size, boolean primary, String description) {
        return new IconButton(type, size, primary, accentSupplier, description);
    }

    public void setOnPrevious(Runnable action) { onPrevious = safe(action); }
    public void setOnPlayPause(Runnable action) { onPlayPause = safe(action); }
    public void setOnStop(Runnable action) { onStop = safe(action); }
    public void setOnNext(Runnable action) { onNext = safe(action); }
    public void setOnSeek(LongConsumer action) { onSeek = action == null ? value -> { } : action; }
    public void setOnVolume(DoubleConsumer action) { onVolume = action == null ? value -> { } : action; }
    public void setOnModeChanged(Consumer<PlayMode> action) { onModeChanged = action == null ? mode -> { } : action; }

    public void setPlaying(boolean playing) {
        playButton.setIconType(playing ? IconPainter.IconType.PAUSE : IconPainter.IconType.PLAY);
    }

    public void setDuration(long durationMillis) {
        this.durationMillis = Math.max(0, durationMillis);
        progress.setDuration(this.durationMillis);
        total.setText(ProgressSlider.formatTime(this.durationMillis));
    }

    public void setPosition(long positionMillis) {
        progress.setPosition(positionMillis);
        elapsed.setText(ProgressSlider.formatTime(positionMillis));
    }

    public long getPosition() { return progress.getPosition(); }
    public double getVolume() { return volume.getValue(); }

    public void adjustVolume(double amount) {
        setVolume(volume.getValue() + amount);
        onVolume.accept(volume.getValue());
    }

    public void setVolume(double value) {
        double normalized = Math.max(0, Math.min(1, value));
        volume.setValue(normalized);
        muted = normalized == 0;
        volumeButton.setIconType(muted ? IconPainter.IconType.MUTED : IconPainter.IconType.VOLUME);
        if (!muted) lastVolume = (int) Math.round(normalized * 100);
    }

    public void setAccent(Color accent) {
        repaint();
    }

    public PlayMode getPlayMode() {
        return PlayMode.values()[modeIndex];
    }

    private void cycleMode() {
        modeIndex = (modeIndex + 1) % PlayMode.values().length;
        PlayMode mode = getPlayMode();
        modeButton.setIconType(mode == PlayMode.SEQUENCE ? IconPainter.IconType.REPEAT
                : mode == PlayMode.REPEAT_ONE ? IconPainter.IconType.REPEAT_ONE : IconPainter.IconType.SHUFFLE);
        modeText.setText(mode == PlayMode.SEQUENCE ? "顺序播放"
                : mode == PlayMode.REPEAT_ONE ? "单曲循环" : "随机播放");
        modeButton.setToolTipText(modeText.getText());
        onModeChanged.accept(mode);
    }

    private void toggleMute() {
        if (muted) {
            muted = false;
            volume.setValue(lastVolume / 100.0);
            volumeButton.setIconType(IconPainter.IconType.VOLUME);
            onVolume.accept(lastVolume / 100.0);
        } else {
            lastVolume = Math.max(1, (int) Math.round(volume.getValue() * 100));
            muted = true;
            volume.setValue(0);
            volumeButton.setIconType(IconPainter.IconType.MUTED);
            onVolume.accept(0);
        }
    }

    private void volumeChanged(double value) {
        muted = value == 0;
        if (value > 0) lastVolume = (int) Math.round(value * 100);
        volumeButton.setIconType(muted ? IconPainter.IconType.MUTED : IconPainter.IconType.VOLUME);
        onVolume.accept(value);
    }

    private Runnable safe(Runnable action) { return action == null ? () -> { } : action; }
}