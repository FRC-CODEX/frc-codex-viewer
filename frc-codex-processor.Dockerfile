FROM python:3.12

COPY requirements.txt /
COPY processor /processor

RUN pip3 install --upgrade pip
RUN pip3 install -r requirements.txt

USER root
RUN mkdir _HTTP_CACHE
RUN chown -R nobody _HTTP_CACHE

USER nobody
CMD ["python3", "-m", "processor.run"]
