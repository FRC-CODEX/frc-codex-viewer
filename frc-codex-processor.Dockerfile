FROM python:3.14

COPY requirements.txt /

RUN pip3 install --upgrade pip
RUN pip3 install -r requirements.txt

COPY processor /processor

USER root
RUN mkdir _HTTP_CACHE
RUN chown -R nobody _HTTP_CACHE

USER nobody
CMD ["python3", "-m", "processor.run"]
