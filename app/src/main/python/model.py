from paddleocr import PaddleOCR
# import process
# from pix2tex.cli import LatexOCR
# from PIL import Image
# import pytesseract
from dashscope import MultiModalConversation
import dashscope
# import time


def paddle_ocr(img_path):
    ocr = (PaddleOCR(use_angle_cls=False, lang="ch", show_log=False))
    # img_path = process.preprocess(img_path)
    result = ocr.ocr(img_path, cls=False)[0]

    # 获取题目文本
    questionList = [line[1][0] for line in result]

    text = ""
    # 将数组转换为字符串
    for str in questionList :
        text += str
    res = {
        'text': text
    }
    return res

def qwen_ocr(img_path):
    dashscope.api_key = ''                  # 输入通义千问API
    local_file_path1 = img_path
    messages = [{
        'role': 'system',
        'content': [{
            'text': '你是一个文本助手，你的任务是OCR。'  # 注意只需要识别图片中的内容，不需要做解答。并且不要添加任何多于的东西，回答只需要识别文字内容即可。
        }]
    }, {
        'role':
            'user',
        'content': [
            {
                'image': local_file_path1
            },
            {
                'text': '请识别出图中所有文字内容,公式请用latex形式表示。'
            },
        ]
    }]
    response = MultiModalConversation.call(model='qwen-vl-plus', messages=messages)
    # print(response["output"]["choices"][0]["message"]["content"])       # plus with "text"
    res = {
        'text': response["output"]["choices"][0]["message"]["content"][0]["text"]
    }
    return res
#
#
# def latex_ocr(img_path):
#     img = Image.open(img_path)
#     model = LatexOCR()
#     return model(img)
#
#
# def tesseract_ocr(img_path):
#     pytesseract.pytesseract.tesseract_cmd = r'D:\Program Files\tesseractOCR\tesseract.exe'
#     img = Image.open(image_path)
#     text = pytesseract.image_to_string(img, lang='chi_sim')
#     return text

if __name__ == '__main__':
    # ti_st = time.time()
    for i in range(21, 43):
        image_path = "resources/" + str(i) + ".jpg"
        print(image_path)
        text = paddle_ocr(image_path)
        print(text["text"])
    # ti_ed = time.time()
    # print(ti_ed - ti_st)


