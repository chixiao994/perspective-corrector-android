import os
import io
import numpy as np
from PIL import Image

from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.widget import Widget
from kivy.graphics import Color, Rectangle, Line, Ellipse
from kivy.graphics.texture import Texture
from kivy.utils import platform
from kivy.metrics import dp

if platform == 'android':
    from android.permissions import request_permissions, Permission
    request_permissions([
        Permission.READ_EXTERNAL_STORAGE,
        Permission.WRITE_EXTERNAL_STORAGE,
        Permission.READ_MEDIA_IMAGES,
    ])


def rgb_to_texture(img_rgb: np.ndarray) -> Texture:
    """RGB numpy -> Kivy Texture（Kivy 原点在左下，需要垂直翻转）"""
    flipped = np.flipud(img_rgb)
    h, w = flipped.shape[:2]
    tex = Texture.create(size=(w, h), colorfmt='rgb')
    tex.blit_buffer(flipped.tobytes(), colorfmt='rgb', bufferfmt='ubyte')
    return tex


class ImageCanvas(Widget):
    def __init__(self, app, **kwargs):
        super().__init__(**kwargs)
        self.app = app
        self.texture = None
        self.img_rect = None  # (x, y, w, h, scale_x, scale_y)
        self.bind(pos=self._on_layout, size=self._on_layout)

    def _on_layout(self, *args):
        self.redraw()

    def set_image(self, img_rgb):
        self.texture = rgb_to_texture(img_rgb)
        self.redraw()

    def redraw(self):
        self.canvas.clear()
        if self.texture is None or self.width <= 1 or self.height <= 1:
            return
        tw, th = self.texture.size
        scale = min(self.width / tw, self.height / th)
        dw, dh = tw * scale, th * scale
        dx = self.x + (self.width - dw) / 2
        dy = self.y + (self.height - dh) / 2
        self.img_rect = (dx, dy, dw, dh, dw / tw, dh / th)

        with self.canvas:
            Color(1, 1, 1, 1)
            Rectangle(texture=self.texture, pos=(dx, dy), size=(dw, dh))

        self._draw_points()

    def _draw_points(self):
        if not self.app.points or self.img_rect is None or self.app.show_corrected:
            return
        dx, dy, dw, dh, sx, sy = self.img_rect
        img_h = self.app.original_img.shape[0]
        pts = []
        for (px, py) in self.app.points:
            cx = dx + px * sx
            cy = dy + (img_h - py) * sy
            pts.append((cx, cy))
        with self.canvas:
            Color(1, 0, 0, 1)
            for (cx, cy) in pts:
                r = dp(6)
                Ellipse(pos=(cx - r, cy - r), size=(r * 2, r * 2))
            if len(pts) == 4:
                Color(0, 1, 0, 1)
                for i in range(4):
                    x1, y1 = pts[i]
                    x2, y2 = pts[(i + 1) % 4]
                    Line(points=[x1, y1, x2, y2], width=dp(1.5))

    def on_touch_down(self, touch):
        if not self.collide_point(*touch.pos):
            return False
        if (self.app.original_img is None or self.app.show_corrected
                or len(self.app.points) >= 4 or self.img_rect is None):
            return False
        dx, dy, dw, dh, sx, sy = self.img_rect
        if not (dx <= touch.x <= dx + dw and dy <= touch.y <= dy + dh):
            return False
        img_h, img_w = self.app.original_img.shape[:2]
        img_x = int((touch.x - dx) / sx)
        img_y = int((img_h - 1) - (touch.y - dy) / sy)
        img_x = max(0, min(img_w - 1, img_x))
        img_y = max(0, min(img_h - 1, img_y))
        self.app.points.append((img_x, img_y))
        self.redraw()
        self.app.update_status()
        return True


class PerspectiveApp(App):
    def build(self):
        self.title = "透视校正"
        self.original_img = None
        self.corrected_img = None
        self.points = []
        self.show_corrected = False

        root = BoxLayout(orientation='vertical')

        top = BoxLayout(size_hint_y=None, height=dp(48), spacing=dp(4), padding=dp(4))
        for text, cb in [
            ('打开', self.open_image),
            ('校正', self.do_correct),
            ('保存', self.save_image),
            ('重置', self.reset_points),
            ('切换', self.toggle_view),
        ]:
            top.add_widget(Button(text=text, on_press=cb))
        root.add_widget(top)

        self.canvas_widget = ImageCanvas(self)
        root.add_widget(self.canvas_widget)

        self.status = Label(text='请打开图片', size_hint_y=None, height=dp(32))
        root.add_widget(self.status)
        return root

    def update_status(self):
        if self.original_img is None:
            self.status.text = '请打开图片'
        elif self.show_corrected:
            self.status.text = '正在显示校正结果'
        else:
            self.status.text = f'已选 {len(self.points)}/4 个点'

    # ---------- 文件操作 ----------
    def open_image(self, *args):
        if platform == 'android':
            try:
                from androidstorage4kivy import Chooser
                Chooser(self._on_android_file_selected).choose_content('image/*')
            except Exception as e:
                self.status.text = f'打开失败: {e}'
        else:
            from tkinter import Tk, filedialog
            r = Tk(); r.withdraw()
            path = filedialog.askopenfilename(
                filetypes=[('图片', '*.jpg *.jpeg *.png *.bmp *.tiff')])
            r.destroy()
            if path:
                self._load_image(path)

    def _on_android_file_selected(self, uri_list):
        if not uri_list:
            return
        try:
            from androidstorage4kivy import SharedStorage
            path = SharedStorage().copy_from_shared(uri_list[0])
            self._load_image(path)
        except Exception as e:
            self.status.text = f'读取失败: {e}'

    def _load_image(self, path):
        try:
            img = Image.open(path).convert('RGB')
            self.original_img = np.array(img)
            self.corrected_img = None
            self.points = []
            self.show_corrected = False
            self.canvas_widget.set_image(self.original_img)
            self.update_status()
        except Exception as e:
            self.status.text = f'加载失败: {e}'

    # ---------- 交互 ----------
    def reset_points(self, *args):
        self.points = []
        self.corrected_img = None
        self.show_corrected = False
        if self.original_img is not None:
            self.canvas_widget.set_image(self.original_img)
        self.update_status()

    def toggle_view(self, *args):
        if self.corrected_img is None:
            return
        self.show_corrected = not self.show_corrected
        src = self.corrected_img if self.show_corrected else self.original_img
        self.canvas_widget.set_image(src)
        self.update_status()

    # ---------- 透视校正 ----------
    def do_correct(self, *args):
        if self.original_img is None:
            self.status.text = '请先打开图片'; return False
        if len(self.points) != 4:
            self.status.text = '请先选满 4 个角点'; return False

        pts = np.array(self.points, dtype=np.float32)
        center = np.mean(pts, axis=0)
        angles = np.arctan2(pts[:, 1] - center[1], pts[:, 0] - center[0])
        pts_sorted = pts[np.argsort(angles)]
        tl_idx = np.argmin(np.sum(pts_sorted, axis=1))
        pts_ordered = np.roll(pts_sorted, -tl_idx, axis=0)
        tl, tr, br, bl = pts_ordered

        width = int((np.linalg.norm(tr - tl) + np.linalg.norm(br - bl)) / 2)
        height = int((np.linalg.norm(bl - tl) + np.linalg.norm(br - tr)) / 2)
        if width < 2 or height < 2:
            self.status.text = '选点区域太小'; return False

        dst = np.array([[0, 0], [width - 1, 0],
                        [width - 1, height - 1], [0, height - 1]], dtype=np.float32)
        coeffs = self._perspective_coeffs(pts_ordered, dst)

        corrected = Image.fromarray(self.original_img).transform(
            (width, height), Image.PERSPECTIVE, coeffs, Image.BICUBIC)
        self.corrected_img = np.array(corrected)
        self.show_corrected = True
        self.canvas_widget.set_image(self.corrected_img)
        self.update_status()
        return True

    @staticmethod
    def _perspective_coeffs(src_pts, dst_pts):
        """解 PIL Image.transform(PERSPECTIVE) 所需的 8 个系数"""
        m = []
        for (sx, sy), (dx, dy) in zip(src_pts, dst_pts):
            m.append([dx, dy, 1, 0, 0, 0, -sx * dx, -sx * dy])
            m.append([0, 0, 0, dx, dy, 1, -sy * dx, -sy * dy])
        A = np.array(m, dtype=np.float64)
        B = np.array(src_pts, dtype=np.float64).flatten()
        return np.linalg.solve(A, B)

    # ---------- 保存 ----------
    def save_image(self, *args):
        if self.corrected_img is None:
            self.status.text = '没有可保存的结果'; return
        pil = Image.fromarray(self.corrected_img)
        if platform == 'android':
            try:
                from androidstorage4kivy import SharedStorage
                tmp = os.path.join(self.user_data_dir, 'corrected.jpg')
                pil.save(tmp, 'JPEG', quality=95)
                SharedStorage().copy_to_shared(
                    tmp, collection='Pictures', file_name='corrected.jpg')
                self.status.text = '已保存到相册 Pictures/'
            except Exception as e:
                self.status.text = f'保存失败: {e}'
        else:
            from tkinter import Tk, filedialog
            r = Tk(); r.withdraw()
            path = filedialog.asksaveasfilename(defaultextension='.jpg')
            r.destroy()
            if path:
                pil.save(path)
                self.status.text = f'已保存: {path}'


if __name__ == '__main__':
    PerspectiveApp().run()
