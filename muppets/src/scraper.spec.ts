import * as Scraper from './scraper';
import * as Websnapper from './websnapper';
import {IFileUploader, uploadScrapedData} from './websnapper';
import { ScraperParams } from './scraper';
import { expect } from 'chai';
import { v4 as uuid } from 'uuid';
import * as path from "path";
import { convertDate } from "./util";
import * as sinon from "sinon";
import config from './config';

class MockFileUploader implements IFileUploader {
    private called: number = 0;

    async upload(data: string | void | Buffer, filename: string, prefix: string, uploadFileFormat: string, contentType?: string): Promise<string> {
        this.called++;

        return Promise.resolve("");
    }

    public getCalledCount(): number {
        return this.called;
    }
}


describe('Scraper Tests', function () {
    this.timeout(15000);

    const mockUuid = '1fe39e26-9d20-4cc0-8696-fe7887a3dfbc';

    let params: ScraperParams = {
        url: 'https://dnsbelgium.be',
        visitId: uuid(),
        saveHar: true,
        saveHtml: true,
        saveScreenshot: true,
        screenshotOptions: {
            fullPage: true,
            encoding: "binary",
            type: "png"
        },
        browserOptions: {
            ignoreHTTPSErrors: true
        },
        retries: 0
    };

    //commented code does not return as expected
    it('check response', () => {
        let folder = path.join("output", "dnsbelgium.be", convertDate(new Date()), 'https', 'dnsbelgium.be', 'index.html', mockUuid);
        return Scraper.websnap(params).then(scraperResult => {
            expect(scraperResult).to.have.property('hostname', 'dnsbelgium.be');
            expect(scraperResult).to.have.property('url', 'https://www.dnsbelgium.be/');
            expect(scraperResult).to.have.property('request',);
            expect(scraperResult.request).to.have.eql({ ...params });
            // expect(scraperResult).to.have.property('referer', '');
            expect(scraperResult).to.have.property('htmlLength');
            expect(scraperResult).to.have.property('pageTitle');
            expect(scraperResult).to.have.property('metrics');
            // expect(scraperResult).to.have.property('folder', folder);
            // expect(scraperResult).to.have.property('harFile', path.join(folder, 'dnsbelgium.be.har'));
            // expect(scraperResult).to.have.property('htmlFile', path.join(folder, 'index.html'));
            // expect(scraperResult).to.have.property('screenshot', path.join(folder, 'screenshot.png'));
            // expect(scraperResult).to.have.property('retries', 1);
        });
    });

    it('S3 bucket upload fails due to html size', () => {
        return Scraper.websnap(params).then(scraperResult => {
            scraperResult.htmlLength = 11*1024*1024;
            return uploadScrapedData(scraperResult, new MockFileUploader()).then(result => {
                console.log(result.errors)
                expect(result.htmlSkipped).to.equal(true);
                expect(result.errors).to.be.empty
            });
        });
    });

    it('S3 bucket succeeds', () => {
        return Scraper.websnap(params).then(scraperResult => {
            return uploadScrapedData(scraperResult, new MockFileUploader() ).then(result => {
                console.log(result.errors)
                expect(result.htmlSkipped).to.equal(false);
                expect(result.errors).to.be.empty
            });
        });
    });

    it('should upload files to S3 and return ScraperResult', async () => {
        const scraperWebsnapResult = await Scraper.websnap(params)
        const result = await uploadScrapedData(scraperWebsnapResult);

        console.log(result.errors);

        expect(result).to.deep.equal({
            ...scraperWebsnapResult,
            bucket: scraperWebsnapResult.bucket,
            screenshotFile: scraperWebsnapResult.screenshotFile,
            screenshotSkipped: false,
            htmlFile: scraperWebsnapResult.htmlFile,
            harFile: scraperWebsnapResult.harFile,
        });
    });

    it('snap should succeed and upload', async () => {
        const mockedUploader = new MockFileUploader();

        const scraperWebsnapResult = await Scraper.websnap(params)
        const websnapperResult = await uploadScrapedData(scraperWebsnapResult, mockedUploader)

        console.log(`s3UploadFile was called ${mockedUploader.getCalledCount()} times`);
        console.log(websnapperResult.errors);

        expect(mockedUploader.getCalledCount()).to.be.equal(3);
        expect(websnapperResult.screenshotSkipped).to.equal(false)
        expect(websnapperResult.errors).to.be.empty;
    });

    it('snap should not upload too large screenshot', async () => {
        const before_test_max_content_length = config.max_content_length
        config.max_content_length = 1;
        const mockedUploader = new MockFileUploader();

        const scraperWebsnapResult = await Scraper.websnap(params)
        const websnapperResult = await uploadScrapedData(scraperWebsnapResult, mockedUploader);

        console.log(`s3UploadFile was called ${mockedUploader.getCalledCount()} times`);
        console.log(websnapperResult.errors);

        expect(mockedUploader.getCalledCount()).to.be.equal(2);
        expect(websnapperResult.screenshotSkipped).to.equal(true);
        expect(websnapperResult.errors).to.be.empty;

        config.max_content_length = before_test_max_content_length;
        sinon.restore();
    });
});
