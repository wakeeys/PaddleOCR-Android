from flask import Flask, request, jsonify
from PIL import Image
import io
import base64
import model
import os

app = Flask(__name__)


def process_image(image_filename):
    text_result = model.paddle_ocr(image_filename)              # use paddle_ocr model
    return text_result


@app.route('/predict', methods=['POST'])
def predict():
    if 'image' not in request.files:
        return jsonify({'error': 'No image provided'}), 400

    image_file = request.files['image']
    image = Image.open(image_file.stream)

    save_path = os.path.join(app.root_path, 'resources')
    if not os.path.exists(save_path):
        os.makedirs(save_path)

    image_filename = os.path.join(save_path, 'uploaded_image.jpg')
    image.save(image_filename)

    # 处理图像并获取结果
    text_result = process_image(image_filename)

    # 将处理后的图像转换为字节流
    img_io = io.BytesIO()
    image.save(img_io, 'JPEG')
    img_io.seek(0)
    img_base64 = base64.b64encode(img_io.getvalue()).decode('utf-8')

    response = {
        'text': text_result,
        'image': img_base64
    }
    print(response)

    return jsonify(response)


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
